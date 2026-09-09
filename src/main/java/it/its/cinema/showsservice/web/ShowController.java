package it.its.cinema.showsservice.web;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import it.its.cinema.showsservice.domain.MovieNotFoundException;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.domain.ShowNotFoundException;
import it.its.cinema.showsservice.service.ShowService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
 * @RestController = @Controller + @ResponseBody: il valore restituito
 * diventa il corpo della risposta, serializzato in JSON da Jackson.
 */
@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService service;

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
                    array = @ArraySchema(schema = @Schema(implementation = Show.class)))))
    @GetMapping
    public Page<Show> findAll(
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return service.findAll(pageable);
    }

    /** PASSO 3.5 — la query di dominio: spettacoli di un film fra due date. */
    @Operation(summary = "Spettacoli di un film in un intervallo di date")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Elenco restituito",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = Show.class)))))
    @GetMapping("/ricerca")
    public List<Show> ricerca(
            @Parameter(description = "Identificativo del film", example = "1")
            @RequestParam Long movieId,
            @Parameter(description = "Inizio dell'intervallo", example = "2026-10-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime da,
            @Parameter(description = "Fine dell'intervallo", example = "2026-10-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime a) {
        return service.perFilmEIntervallo(movieId, da, a);
    }

    /** GET /shows/{id} — 200 se c'e', 404 se non c'e'. */
    @Operation(
            summary = "Cerca uno spettacolo per ID",
            description = "Restituisce il singolo spettacolo con il film associato, "
                    + "il prezzo base e i posti ancora disponibili.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Spettacolo trovato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Show.class))),
            // senza content, Swagger non promette un corpo: e' corretto,
            // perche' oggi il 404 restituisce solo un messaggio di testo.
            // Al G4, con ProblemDetail, qui ci andra' il suo schema.
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "L'ID non e' un numero",
                    content = @Content)
    })
    @GetMapping("/{id}")
    public Show perId(@PathVariable Long id) {
        return service.findById(id);
    }

   @Operation(summary = "Elimina uno spettacolo")
    @ApiResponses({
            // 204 e non 200: e' andata bene e non c'e' niente da dire
            @ApiResponse(responseCode = "204", description = "Eliminato, nessun corpo"),
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato",
                    content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

     /**
     * Il film si indica con movie.id e deve esistere gia' nel catalogo.
     *
     * id e availableSeats, se inviati, vengono IGNORATI: non sono dati del
     * client, sono conseguenze. Oggi e' il service a ricordarsene; al G4 il
     * DTO in ingresso non avra' nemmeno quei campi, e la regola smettera' di
     * dipendere dalla memoria di chi scrive il codice.
     */
    @Operation(summary = "Crea uno spettacolo",
            description = "Il film si indica con movie.id e deve esistere. "
                    + "id e availableSeats eventualmente inviati vengono ignorati.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Creato, con header Location",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Show.class))),
            @ApiResponse(responseCode = "400", description = "Dati non validi o movie.id mancante",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Il film indicato non esiste",
                    content = @Content)
    })
    @PostMapping
    public ResponseEntity<Show> crea(@RequestBody Show richiesta) {
        // Si passano al service solo i quattro campi che il client puo'
        // decidere: id e availableSeats eventualmente inviati muoiono qui.
        Show creato = service.create(
                richiesta.getMovie() == null ? null : richiesta.getMovie().getId(),
                richiesta.getStartTime(),
                richiesta.getBasePrice(),
                richiesta.getTotalSeats());

        // 201 con Location: il client sa dove e' finita la risorsa che ha
        // creato, senza doverla cercare. E' meta' del significato di "created".
        return ResponseEntity.created(URI.create("/shows/" + creato.getId()))
                .body(creato);
    }

    /**
     * PUT: si inviano TUTTI i campi modificabili, non solo quelli
     * cambiati. 
     */
    @Operation(summary = "Aggiorna orario e prezzo di uno spettacolo",
            description = "Il film e i posti totali non si modificano: uno spettacolo "
                    + "con un altro film e' uno spettacolo diverso, e chi ha gia' "
                    + "prenotato non si aspetta che la sala si rimpicciolisca.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggiornato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Show.class))),
            @ApiResponse(responseCode = "400", description = "Dati non validi", content = @Content),
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato", content = @Content)
    })
    @PutMapping("/{id}")
    public Show update(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id,
            @RequestBody Show richiesta) {
        return service.update(id, richiesta.getStartTime(), richiesta.getBasePrice());
    }

    /**
     * POST e non PUT: non si sta sostituendo una risorsa, si sta chiedendo di
     * ESEGUIRE un'operazione. E non e' idempotente — chiamarla due volte
     * riserva quattro posti, non due.
     */
    @Operation(summary = "Riserva dei posti",
            description = "Scala i posti richiesti dalla disponibilita'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posti riservati",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Show.class))),
            @ApiResponse(responseCode = "400", description = "Quantita' non valida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato", content = @Content),
            @ApiResponse(responseCode = "409", description = "Posti insufficienti", content = @Content)
    })
    @PostMapping("/{id}/reserve")
    public ResponseEntity<Show> prenota(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id,
            @Parameter(description = "Quanti posti riservare", example = "2")
            @RequestParam int quantity) {
        Show show= service.reserveSeats(id, quantity);
        return ResponseEntity.ok(show);
    }

    @Operation(summary = "Rilascia dei posti",
            description = "Restituisce i posti al pubblico. Non si supera mai il "
                    + "numero di posti totali, nemmeno chiamandola due volte.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posti rilasciati",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Show.class))),
            @ApiResponse(responseCode = "400", description = "Quantita' non valida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Spettacolo non trovato", content = @Content)
    })
    @PostMapping("/{id}/release")
    public ResponseEntity<Show>  rilascia(
            @Parameter(description = "Identificativo dello spettacolo", example = "1")
            @PathVariable Long id,
            @Parameter(description = "Quanti posti rilasciare", example = "2")
            @RequestParam int quantity) {
        Show show = service.releaseSeats(id, quantity);
        return ResponseEntity.ok(show);
    }

    /**
     * Traduce l'eccezione di dominio in uno status HTTP. Senza questo,
     * un'eccezione non gestita diventerebbe un 500: "non l'ho trovato" e
     * "mi sono rotto" sono due cose diverse, e il client deve poterle distinguere.
     *
     * Vale solo per QUESTO controller
     *
     * @Hidden serve davvero. Senza, springdoc DEDUCE da questo handler un 404
     * e lo appiccica a TUTTI i metodi del controller, GET /shows compreso:
     * un elenco non puo' rispondere 404, e la documentazione direbbe il falso.
     * Con @Hidden l'inferenza si spegne e restano solo gli @ApiResponse
     * dichiarati a mano, che sono precisi.
     */
    @Hidden
    @ExceptionHandler(ShowNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(ShowNotFoundException e) {
        return e.getMessage();
    }

    /**
     * Anche "il film non esiste" e' un 404, e non un 500: senza questo handler
     * la POST finirebbe contro il vincolo di foreign key e il client leggerebbe
     * "errore interno" per un dato sbagliato che ha mandato lui.
     */
    @Hidden
    @ExceptionHandler(MovieNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String movieNotFound(MovieNotFoundException e) {
        return e.getMessage();
    }

    /**
     * Le validazioni del dominio (prezzo negativo, posti non positivi,
     * quantita' a zero) lanciano IllegalArgumentException: senza questo
     * handler sarebbero 500, mentre sono richieste sbagliate del client.
     * E' il 400 che gli @ApiResponse qui sopra promettono da subito.
     */
    @Hidden
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    /**
     * PASSO 3.8 — il conflitto di concorrenza e' un 409, non un 500.
     *
     * 500 vuol dire "colpa nostra, riprovare non serve". Qui invece la
     * richiesta era legittima e riprovare ha ottime probabilita' di riuscire:
     * e' esattamente cosa significa 409 Conflict.
     */
    @Hidden
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflittoDiConcorrenza(OptimisticLockingFailureException e) {
        return "Qualcun altro ha modificato lo spettacolo mentre completavi "
                + "l'operazione. Riprova.";
    }
}
