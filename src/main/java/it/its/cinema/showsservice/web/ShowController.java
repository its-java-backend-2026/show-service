package it.its.cinema.showsservice.web;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.service.ShowService;
import it.its.cinema.showsservice.web.dto.CreateShowRequest;
import it.its.cinema.showsservice.web.dto.SeatsRequest;
import it.its.cinema.showsservice.web.dto.ShowResponse;
import it.its.cinema.showsservice.web.dto.UpdateShowRequest;
import it.its.cinema.showsservice.web.mapper.ShowMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * PASSO 1.8 — lo strato che parla HTTP, e SOLO HTTP.
 *
 * La regola del passo 1.2: nessuno strato salta un livello.
 * Il controller conosce ShowService e non ha la minima idea che esista
 * un ShowRepository. Se qui comparisse un repository, i tre strati
 * sarebbero gia' due.
 *
 * PASSO 4.1 — DA OGGI ENTRANO ED ESCONO DTO, NON ENTITA'.
 *
 * Il controller non nomina piu' Show nelle firme pubbliche: riceve
 * CreateShowRequest e UpdateShowRequest, restituisce ShowResponse. L'entita'
 * resta una variabile locale, il tempo di passare dal service al mapper.
 * Il JSON in uscita non e' piu' il riflesso della tabella, ed e' il punto:
 * schema e contratto HTTP possono ora cambiare ognuno per conto suo.
 *
 * PASSO 4.6 — QUI NON CI SONO PIU' @ExceptionHandler.
 *
 * Erano cinque, e quattro erano identici a quelli di MovieController. Ora
 * stanno tutti in GestoreErrori, che li applica a entrambi i controller e
 * risponde a tutti nello stesso formato, ProblemDetail. Sparisce anche il
 * @Hidden che serviva a impedire a springdoc di dedurre un 404 su ogni rotta:
 * senza handler locali non c'e' piu' niente da dedurre.
 */
@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService service;
    private final ShowMapper mapper;

    /**
     * PASSO 3.4 — paginazione e ordinamento.
     *
     * Un elenco senza limiti e' una bomba a orologeria: funziona con tre
     * righe e affoga con trecentomila. Pageable arriva dai parametri della
     * query, @PageableDefault decide cosa fare quando non ci sono.
     * Provare:  GET /shows?page=0&size=5&sort=startTime,desc
     */
    @Operation(summary = "Elenca gli spettacoli",
            description = "Elenco paginato, ordinato per orario di inizio. "
                    + "Parametri: page, size, sort (es. startTime,desc).")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Pagina restituita, anche vuota",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ShowResponse.class))))
    @GetMapping
    public Page<ShowResponse> findAll(
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return mapper.toResponse(service.findAll(pageable));
    }

    /** PASSO 3.5 — la query di dominio: spettacoli di un film fra due date. */
    @Operation(summary = "Spettacoli di un film in un intervallo di date")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Elenco restituito",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ShowResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Parametri mancanti o mal formati",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/ricerca")
    public List<ShowResponse> ricerca(
            @Parameter(description = "Identificativo del film", example = "1")
            @RequestParam Long movieId,
            @Parameter(description = "Inizio dell'intervallo", example = "2026-10-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime da,
            @Parameter(description = "Fine dell'intervallo", example = "2026-10-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime a) {
        return mapper.toResponse(service.perFilmEIntervallo(movieId, da, a));
    }

    /** GET /shows/{id} — 200 se c'e', 404 se non c'e'. */
    @Operation(
            summary = "Cerca uno spettacolo per ID",
            description = "Restituisce il singolo spettacolo con il titolo del film, "
                    + "il prezzo base e i posti ancora disponibili.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Spettacolo trovato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ShowResponse.class))),
            // PASSO 4.6 — ora il corpo dell'errore c'e', ed e' documentato:
            // fino a ieri era una stringa secca e qui si scriveva @Content vuoto.
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "400", description = "L'ID non e' un numero",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ShowResponse perId(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id) {
        return mapper.toResponse(service.findById(id));
    }

    @Operation(summary = "Elimina uno spettacolo")
    @ApiResponses({
            // 204 e non 200: e' andata bene e non c'e' niente da dire
            @ApiResponse(responseCode = "204", description = "Eliminato, nessun corpo"),
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Il film si indica con movieId e deve esistere gia' nel catalogo.
     *
     * PASSO 4.1 — id e availableSeats non vengono piu' "ignorati": non esistono
     * proprio in CreateShowRequest. Fino a ieri arrivavano e il controller li
     * buttava via a mano, ricordandosene; ora la regola sta nel tipo e non
     * dipende piu' dalla memoria di chi scrive il codice.
     */
    @Operation(summary = "Crea uno spettacolo",
            description = "Il film si indica con movieId e deve esistere in catalogo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Creato, con header Location",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ShowResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dati non validi",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Il film indicato non esiste",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<ShowResponse> crea(@Valid @RequestBody CreateShowRequest richiesta) {
        Show creato = service.create(
                richiesta.movieId(),
                richiesta.startTime(),
                richiesta.basePrice(),
                richiesta.totalSeats());

        // 201 con Location: il client sa dove e' finita la risorsa che ha
        // creato, senza doverla cercare. E' meta' del significato di "created".
        return ResponseEntity.created(URI.create("/shows/" + creato.getId()))
                .body(mapper.toResponse(creato));
    }

    /**
     * PUT: si inviano TUTTI i campi modificabili, non solo quelli cambiati.
     */
    @Operation(summary = "Aggiorna orario e prezzo di uno spettacolo",
            description = "Il film e i posti totali non si modificano: uno spettacolo "
                    + "con un altro film e' uno spettacolo diverso, e chi ha gia' "
                    + "prenotato non si aspetta che la sala si rimpicciolisca. "
                    + "Per questo UpdateShowRequest non ha quei campi.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggiornato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ShowResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dati non validi",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Modifica concorrente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}")
    public ShowResponse update(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateShowRequest richiesta) {
        return mapper.toResponse(
                service.update(id, richiesta.startTime(), richiesta.basePrice()));
    }

    /**
     * POST e non PUT: non si sta sostituendo una risorsa, si sta chiedendo di
     * ESEGUIRE un'operazione. E non e' idempotente — chiamarla due volte
     * riserva quattro posti, non due.
     *
     * PASSO 6.4 — I DUE PASSI DELLA SAGA, VISTI DA QUESTA PARTE.
     *
     * Questa rotta e la sua compensazione (/release) sono cio' che
     * booking-service chiamera' dal passo 6.10. Da oggi i parametri arrivano
     * in un corpo JSON (SeatsRequest) e non piu' nella query string, perche'
     * insieme alla quantita' viaggia il sagaId.
     *
     * shows-service NON sa che esiste una saga, e non deve saperlo: riceve un
     * identificativo opaco, lo scrive nei log e lo dimentica. Il coordinamento
     * e' un problema di chi coordina.
     */
    @Operation(summary = "Riserva dei posti",
            description = "Scala i posti richiesti dalla disponibilita'. "
                    + "Primo passo della saga di prenotazione (G6): la chiama "
                    + "booking-service, che genera e trasmette il sagaId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posti riservati",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ShowResponse.class))),
            @ApiResponse(responseCode = "400", description = "Corpo mancante, sagaId vuoto o quantita' non valida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Posti insufficienti",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/reserve")
    public ShowResponse prenota(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody SeatsRequest richiesta) {
        return mapper.toResponse(
                service.reserveSeats(id, richiesta.quantity(), richiesta.sagaId()));
    }

    /**
     * La COMPENSAZIONE di /reserve: non un "annulla", che nei sistemi
     * distribuiti non esiste, ma un'operazione nuova che rimette a posto.
     * Dal G8 e' quello che parte quando il pagamento viene rifiutato.
     */
    @Operation(summary = "Rilascia dei posti",
            description = "Restituisce i posti al pubblico. Non si supera mai il "
                    + "numero di posti totali, nemmeno chiamandola due volte. "
                    + "E' la compensazione di /reserve, e porta lo stesso sagaId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posti rilasciati",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ShowResponse.class))),
            @ApiResponse(responseCode = "400", description = "Corpo mancante, sagaId vuoto o quantita' non valida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/release")
    public ShowResponse rilascia(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody SeatsRequest richiesta) {
        return mapper.toResponse(
                service.releaseSeats(id, richiesta.quantity(), richiesta.sagaId()));
    }
}
