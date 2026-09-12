package it.its.cinema.showsservice;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.NotEnoughSeatsException;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.domain.ShowNotFoundException;
import it.its.cinema.showsservice.service.ShowService;
import it.its.cinema.showsservice.web.GestoreErrori;
import it.its.cinema.showsservice.web.ShowController;
import it.its.cinema.showsservice.web.mapper.ShowMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PASSO 4.8 — IL TEST DEL LIVELLO WEB, DA SOLO.
 *
 * @WebMvcTest avvia SOLO la fetta web: controller, mapper di Jackson,
 * conversione dei tipi, @Valid, @RestControllerAdvice. Niente database, niente
 * Hibernate, niente Flyway, nessun container Docker. Parte in un secondo e
 * risponde a una domanda precisa: "il contratto HTTP e' quello che dico?"
 *
 * Non risponde a "il salvataggio funziona?": quello e' ShowRepositoryIT, che
 * usa PostgreSQL vero e gira su mvn verify. Due test diversi per due domande
 * diverse, ed e' il motivo per cui nessuno dei due e' lento.
 *
 * ATTENZIONE Boot 4 — DUE TRAPPOLE.
 *
 *  1. Le slice di test sono moduli separati. Serve la dipendenza
 *     spring-boot-starter-webmvc-test, e l'annotazione NON e' piu' in
 *     org.springframework.boot.test.autoconfigure.web.servlet ma in
 *         org.springframework.boot.webmvc.test.autoconfigure
 *     L'IDE, se l'import lo mette da solo, sbaglia.
 *
 *  2. @WebMvcTest carica i @RestController e i @RestControllerAdvice, NON i
 *     @Component. ShowMapper e' un @Component: senza @Import il contesto non
 *     parte, e l'errore parla di una dipendenza mancante, non del mapper.
 *     GestoreErrori lo si importa esplicitamente per la stessa ragione di
 *     chiarezza: e' parte di cio' che questo test verifica.
 *
 * ShowService e' un @MockitoBean (l'erede di @MockBean, deprecato da Boot 3.4):
 * il service qui non si testa, si finge. Il confine del test e' il controller.
 */
@WebMvcTest(ShowController.class)
@Import({ShowMapper.class, GestoreErrori.class})
class ShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShowService service;

    /** Uno spettacolo finto, con il film gia' caricato: il mapper lo legge. */
    private Show spettacoloDiProva() {
        Movie film = new Movie(1L, "Dune - Parte Due", 166);
        Show show = new Show(7L, film, LocalDateTime.of(2027, 1, 15, 21, 0),
                new BigDecimal("9.50"), 120);
        return show;
    }

    @Test
    @DisplayName("GET /shows/{id} appiattisce il film in movieId e movieTitle")
    void dettaglio() throws Exception {
        when(service.findById(7L)).thenReturn(spettacoloDiProva());

        mockMvc.perform(get("/shows/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.movieId").value(1))
                .andExpect(jsonPath("$.movieTitle").value("Dune - Parte Due"))
                .andExpect(jsonPath("$.availableSeats").value(120))
                .andExpect(jsonPath("$.eveningShow").value(true))
                // PASSO 4.1 — la prova che il DTO fa il suo mestiere:
                // l'oggetto movie annidato NON esiste piu' nel JSON.
                .andExpect(jsonPath("$.movie").doesNotExist());
    }

    @Test
    @DisplayName("Uno spettacolo inesistente e' un 404 in formato ProblemDetail")
    void nonTrovato() throws Exception {
        when(service.findById(999L)).thenThrow(new ShowNotFoundException(999L));

        mockMvc.perform(get("/shows/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Spettacolo non trovato"))
                .andExpect(jsonPath("$.type").value("https://cinema.its.it/errori/spettacolo-non-trovato"))
                .andExpect(jsonPath("$.detail").value("Spettacolo non trovato: 999"));
    }

    @Test
    @DisplayName("Un id che non e' un numero e' un 400, non un 500")
    void idNonNumerico() throws Exception {
        mockMvc.perform(get("/shows/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parametro non valido"));
    }

    @Test
    @DisplayName("PASSO 4.5 — un POST senza campi obbligatori e' 400 e dice QUALI")
    void creazioneSenzaCampi() throws Exception {
        mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dati non validi"))
                .andExpect(jsonPath("$.errors.movieId").value("Il film e' obbligatorio"))
                .andExpect(jsonPath("$.errors.startTime").value("L'orario di inizio e' obbligatorio"))
                .andExpect(jsonPath("$.errors.basePrice").value("Il prezzo base e' obbligatorio"))
                .andExpect(jsonPath("$.errors.totalSeats").value("I posti totali sono obbligatori"));

        // E il service non e' mai stato chiamato: @Valid ferma la richiesta
        // PRIMA che tocchi la logica. E' meta' del valore del passo 4.5.
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("PASSO 4.5 — posti totali a zero: il messaggio parla di positivita', non di campo mancante")
    void creazioneConPostiAZero() throws Exception {
        mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":1,"startTime":"2027-01-15T21:00:00",
                                 "basePrice":9.50,"totalSeats":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.totalSeats").value("I posti totali devono essere positivi"));
    }

    @Test
    @DisplayName("PASSO 4.5 — uno spettacolo nel passato e' 400")
    void creazioneNelPassato() throws Exception {
        // La data si calcola, non si scrive: una costante come "2020-01-01"
        // funziona oggi e resta un test valido per sempre, ma una scritta
        // "2027-01-01" per il caso opposto scadrebbe in silenzio. Meglio
        // prendere l'abitudine su entrambi i lati.
        String ieri = LocalDateTime.now().minusDays(1).toString();

        mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":1,"startTime":"%s",
                                 "basePrice":9.50,"totalSeats":120}
                                """.formatted(ieri)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startTime")
                        .value("L'orario di inizio deve essere nel futuro"));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("PASSO 4.5 — @Future tace su un campo assente: e' @NotNull a parlare")
    void startTimeAssenteRestaUnSoloErrore() throws Exception {
        // Un null e' valido per ogni vincolo tranne @NotNull: se @Future
        // rispondesse anche lui, il client leggerebbe due messaggi
        // contraddittori per lo stesso campo dimenticato.
        mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":1,"basePrice":9.50,"totalSeats":120}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startTime")
                        .value("L'orario di inizio e' obbligatorio"));
    }

    @Test
    @DisplayName("PASSO 4.1 — id e availableSeats inviati dal client vengono ignorati")
    void creazioneIgnoraICampiNonSuoi() throws Exception {
        when(service.create(eq(1L), any(), any(), anyInt())).thenReturn(spettacoloDiProva());

        mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999,"availableSeats":3,"version":42,
                                 "movieId":1,"startTime":"2027-01-15T21:00:00",
                                 "basePrice":9.50,"totalSeats":120}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/shows/7"))
                // l'id e' quello che ha assegnato il servizio, non il 999 inviato
                .andExpect(jsonPath("$.id").value(7))
                // availableSeats e' una conseguenza dei posti totali, non il 3 inviato
                .andExpect(jsonPath("$.availableSeats").value(120));

        // il service riceve i quattro campi veri, e nient'altro
        verify(service).create(eq(1L), eq(LocalDateTime.of(2027, 1, 15, 21, 0)),
                eq(new BigDecimal("9.50")), eq(120));
    }

    @Test
    @DisplayName("Posti insufficienti e' un 409, non un 500")
    void postiInsufficienti() throws Exception {
        when(service.reserveSeats(7L, 500, "saga-1"))
                .thenThrow(new NotEnoughSeatsException(7L, 500, 120));

        mockMvc.perform(post("/shows/7/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sagaId":"saga-1","quantity":500}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Posti insufficienti"))
                .andExpect(jsonPath("$.type").value("https://cinema.its.it/errori/posti-insufficienti"));
    }

    /**
     * PASSO 6.4 — il sagaId arriva fino al service.
     *
     * Il test non verifica un comportamento visibile in HTTP: verifica che il
     * controller non lo perda per strada. E' l'unico punto in cui si puo'
     * controllare, perche' da li' in poi il sagaId finisce solo in un log.
     */
    @Test
    @DisplayName("reserve passa al service quantita' E sagaId")
    void reservePassaIlSagaId() throws Exception {
        Show show = new Show(7L, new Movie(1L, "Dune - Parte Due", 166),
                LocalDateTime.of(2027, 1, 15, 21, 0), new BigDecimal("9.50"), 120);
        when(service.reserveSeats(eq(7L), anyInt(), any())).thenReturn(show);

        mockMvc.perform(post("/shows/7/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sagaId":"3f2a1b9c-6d4e-4a7b-9c2f-1e8d0a5b7c31","quantity":2}
                                """))
                .andExpect(status().isOk());

        verify(service).reserveSeats(7L, 2, "3f2a1b9c-6d4e-4a7b-9c2f-1e8d0a5b7c31");
    }

    /**
     * PASSO 6.4 — un sagaId vuoto e' un 400, e il service non viene sfiorato.
     *
     * E' il @NotBlank di SeatsRequest a fermarlo: senza @Valid sul parametro
     * del controller la validazione non scatterebbe e la stringa vuota
     * arriverebbe nei log come identificativo di correlazione inutile.
     */
    @Test
    @DisplayName("Un sagaId vuoto e' un 400 e non arriva al service")
    void sagaIdVuoto() throws Exception {
        mockMvc.perform(post("/shows/7/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sagaId":"   ","quantity":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sagaId").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Il corpo mancante e' un 400 gestito da Spring, non il nostro 500")
    void corpoMancante() throws Exception {
        // Senza "extends ResponseEntityExceptionHandler" in GestoreErrori,
        // questa richiesta finirebbe nel catch-all @ExceptionHandler(Exception)
        // e il client leggerebbe 500 per un errore suo.
        mockMvc.perform(post("/shows/7/reserve"))
                .andExpect(status().isBadRequest());
    }
}
