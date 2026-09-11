package it.its.cinema.showsservice.web;

import it.its.cinema.showsservice.domain.CatalogProviderUnavailableException;
import it.its.cinema.showsservice.domain.MovieInUseException;
import it.its.cinema.showsservice.domain.MovieNotFoundException;
import it.its.cinema.showsservice.domain.MovieTitleAlreadyExistsException;
import it.its.cinema.showsservice.domain.NotEnoughSeatsException;
import it.its.cinema.showsservice.domain.ShowNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PASSO 4.6 — GLI ERRORI, IN UN POSTO SOLO E IN UN FORMATO SOLO.
 *
 * Fino a ieri ogni controller aveva i suoi @ExceptionHandler, e ShowController
 * e MovieController ne avevano quattro identici: stessa logica, due copie, e
 * nessuna garanzia che restassero allineate. Peggio, il formato in uscita erano
 * DUE formati diversi — i nostri handler restituivano una stringa secca, gli
 * errori di Spring un JSON con altri nomi di campo — e il front-end del G2 ha
 * una funzione apposta per indovinare quale dei due sta leggendo.
 *
 * @RestControllerAdvice raccoglie gli handler di TUTTI i controller in una
 * classe sola. ProblemDetail (RFC 7807) impone a tutti la stessa forma:
 *
 *     {
 *       "type":   "https://cinema.its.it/errori/spettacolo-non-trovato",
 *       "title":  "Spettacolo non trovato",
 *       "status": 404,
 *       "detail": "Spettacolo non trovato: 999",
 *       "instance": "/shows/999"
 *     }
 *
 * "type" e' un identificatore stabile, adatto a un if nel codice del client;
 * "detail" e' il messaggio per gli umani e puo' cambiare senza rompere niente.
 * Distinguere i due e' tutto il valore dello standard.
 *
 * PERCHE' extends ResponseEntityExceptionHandler:
 * la classe base sa gia' tradurre in ProblemDetail una ventina di eccezioni di
 * Spring MVC (corpo JSON malformato, parametro obbligatorio mancante, metodo
 * HTTP sbagliato, Content-Type non supportato). Senza di lei quelle finirebbero
 * nel catch-all in fondo e diventerebbero 500: un parametro dimenticato dal
 * client verrebbe raccontato come un guasto del server.
 */
@RestControllerAdvice
@Slf4j
public class GestoreErrori extends ResponseEntityExceptionHandler {

    /** Prefisso dei "type": un URI che identifica la CATEGORIA di errore. */
    private static final String BASE_TYPE = "https://cinema.its.it/errori/";

    // ---------------------------------------------------------------- 404

    @ExceptionHandler({ShowNotFoundException.class, MovieNotFoundException.class})
    public ProblemDetail nonTrovato(RuntimeException e) {
        boolean film = e instanceof MovieNotFoundException;
        return problema(HttpStatus.NOT_FOUND,
                film ? "Film non trovato" : "Spettacolo non trovato",
                film ? "film-non-trovato" : "spettacolo-non-trovato",
                e.getMessage());
    }

    // ---------------------------------------------------------------- 409

    /**
     * Posti insufficienti e' 409, non 500 e nemmeno 400.
     *
     * Non e' 400 perche' la richiesta e' scritta benissimo: e' lo STATO dello
     * spettacolo a renderla impossibile, e la stessa identica richiesta mandata
     * un'ora prima sarebbe andata a buon fine.
     */
    @ExceptionHandler(NotEnoughSeatsException.class)
    public ProblemDetail postiInsufficienti(NotEnoughSeatsException e) {
        return problema(HttpStatus.CONFLICT, "Posti insufficienti",
                "posti-insufficienti", e.getMessage());
    }

    @ExceptionHandler(MovieTitleAlreadyExistsException.class)
    public ProblemDetail titoloDuplicato(MovieTitleAlreadyExistsException e) {
        return problema(HttpStatus.CONFLICT, "Titolo gia' in catalogo",
                "titolo-duplicato", e.getMessage());
    }

    @ExceptionHandler(MovieInUseException.class)
    public ProblemDetail filmInProgrammazione(MovieInUseException e) {
        return problema(HttpStatus.CONFLICT, "Film in programmazione",
                "film-in-programmazione", e.getMessage());
    }

    /**
     * PASSO 3.7 — il conflitto del lock ottimistico.
     *
     * 500 vorrebbe dire "colpa nostra, riprovare non serve". Qui invece la
     * richiesta era legittima e riprovare ha ottime probabilita' di riuscire.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail conflittoDiConcorrenza(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Modifica concorrente",
                "modifica-concorrente",
                "Qualcun altro ha modificato la risorsa mentre completavi "
                        + "l'operazione. Rileggila e riprova.");
    }

    // ---------------------------------------------------------------- 503

    /**
     * PASSO 6.8b — IL FORNITORE ESTERNO NON HA RISPOSTO.
     *
     * 503 e non 500, ed e' la distinzione che vale il passo: il nostro codice
     * ha funzionato, e' un servizio a valle che non c'era. 500 significa
     * "colpa nostra, un bug": manda in caccia la persona sbagliata e dice al
     * client che riprovare e' inutile.
     *
     * Con Retry-After diciamo anche QUANDO riprovare, e un client educato
     * (o un Resilience4j al G7) lo rispetta invece di martellare un servizio
     * che e' gia' in difficolta'.
     */
    @ExceptionHandler(CatalogProviderUnavailableException.class)
    public ResponseEntity<ProblemDetail> fornitoreNonDisponibile(
            CatalogProviderUnavailableException e) {

        ProblemDetail corpo = problema(HttpStatus.SERVICE_UNAVAILABLE,
                "Fornitore non disponibile",
                "fornitore-non-disponibile",
                e.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(corpo);
    }

    // ---------------------------------------------------------------- 400

    /**
     * Le validazioni che restano nel dominio: quantita' non positiva,
     * titolo di ricerca vuoto. Sono richieste sbagliate del client, non guasti.
     *
     * Dal passo 4.5 la maggior parte dei casi non arriva piu' fin qui: li
     * intercetta @Valid sul DTO, molto prima che il service venga chiamato.
     * Questo handler copre le regole che il DTO non puo' esprimere.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail richiestaNonValida(IllegalArgumentException e) {
        return problema(HttpStatus.BAD_REQUEST, "Richiesta non valida",
                "richiesta-non-valida", e.getMessage());
    }

    /** GET /shows/abc — l'id nel path non e' un numero. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoSbagliato(MethodArgumentTypeMismatchException e) {
        return problema(HttpStatus.BAD_REQUEST, "Parametro non valido",
                "parametro-non-valido",
                "Il parametro '" + e.getName() + "' non accetta il valore '"
                        + e.getValue() + "'.");
    }

    /**
     * PASSO 4.5 — il fallimento di @Valid.
     *
     * Il metodo e' un OVERRIDE della classe base, non un @ExceptionHandler
     * nuovo: MethodArgumentNotValidException la gestisce gia' lei, e
     * aggiungerne un secondo handler darebbe un conflitto di ambiguita'
     * all'avvio. Qui si prende il suo ProblemDetail e gli si aggiunge l'elenco
     * dei campi rifiutati.
     *
     * Un 400 che dice solo "richiesta non valida" costringe il client a
     * indovinare. Con "errors" sa esattamente quale campo rimandare:
     *
     *     "errors": { "totalSeats": "I posti totali devono essere positivi" }
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        // LinkedHashMap: l'ordine dei campi resta quello di dichiarazione,
        // e un JSON di errore che cambia ordine a ogni chiamata e' scomodo
        // da leggere e impossibile da confrontare in un test.
        Map<String, String> campi = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(errore ->
                campi.putIfAbsent(errore.getField(), errore.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(errore ->
                campi.putIfAbsent(errore.getObjectName(), errore.getDefaultMessage()));

        ProblemDetail corpo = problema(HttpStatus.BAD_REQUEST, "Dati non validi",
                "dati-non-validi",
                "La richiesta contiene " + campi.size() + " campo/i non valido/i.");
        corpo.setProperty("errors", campi);

        return handleExceptionInternal(ex, corpo, headers, status, request);
    }

    // ---------------------------------------------------------------- 500

    /**
     * L'ultima rete: qualunque cosa non prevista sopra.
     *
     * DUE REGOLE, ed e' l'unico handler in cui contano davvero.
     *
     *  1. Si logga con lo stack trace COMPLETO: e' un bug nostro, e senza
     *     stack trace non si trova. Gli altri handler non loggano nulla,
     *     perche' un 404 non e' un problema del server e riempire i log di
     *     404 significa non vedere piu' i 500 in mezzo.
     *
     *  2. NON si rimanda al client e.getMessage(): il messaggio di
     *     un'eccezione interna puo' contenere frammenti di query, nomi di
     *     tabelle, path del filesystem. Chi ha causato l'errore riceve una
     *     frase generica, chi deve ripararlo lo trova nei log.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail erroreInterno(Exception e) {
        log.error("Errore non gestito", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno",
                "errore-interno",
                "Si e' verificato un errore imprevisto. Se il problema persiste, "
                        + "segnalalo indicando l'ora esatta del tentativo.");
    }

    // ---------------------------------------------------------------- utilita'

    private ProblemDetail problema(HttpStatus stato, String titolo, String tipo, String dettaglio) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(stato, dettaglio);
        p.setTitle(titolo);
        p.setType(URI.create(BASE_TYPE + tipo));
        return p;
    }
}
