package it.its.cinema.showsservice.web;

import java.net.URI;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.MovieInUseException;
import it.its.cinema.showsservice.domain.MovieNotFoundException;
import it.its.cinema.showsservice.domain.MovieTitleAlreadyExistsException;
import it.its.cinema.showsservice.service.MovieService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
 * Il catalogo dei film via HTTP.
 */
@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService service;

    /**
     * GET /movies-> tutto il catalogo
     * GET /movies?title=du -> solo i film il cui titolo contiene "du"
     *
     * PASSO 3.4 — paginazione e ordinamento, come su GET /shows.
     *
     * Un elenco senza limiti e' una bomba a orologeria: funziona con tre righe
     * e affoga con trecentomila. Pageable arriva dai parametri della query,
     * @PageableDefault decide cosa fare quando non ci sono.
     * Provare:  GET /movies?page=0&size=5&sort=title,desc
     *
     * ATTENZIONE: cambia la forma del JSON. Prima era un array di film, ora e'
     * un oggetto con content, totalElements, totalPages, number, size. Chi
     * consuma questa rotta va avvisato: e' un cambiamento incompatibile.
     */
    @Operation(
            summary = "Elenca o cerca i film",
            description = "Senza parametri restituisce il catalogo paginato. "
                    + "Con ?title= restituisce i film il cui titolo contiene il "
                    + "frammento indicato, ignorando maiuscole e minuscole. "
                    + "Una ricerca senza risultati e' un 200 con pagina vuota, non un 404. "
                    + "Parametri di paginazione: page, size, sort (es. title,desc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina restituita, anche vuota",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Movie.class))),
            @ApiResponse(responseCode = "400", description = "Il parametro title e' presente ma vuoto",
                    content = @Content)
    })
    @GetMapping
    public Page<Movie> findAll(
            @Parameter(description = "Frammento di titolo da cercare", example = "dune")
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return title == null
                ? service.findAll(pageable)
                : service.searchByTitle(title, pageable);
    }

    /** GET /movies/{id} — 200 se c'e', 404 se non c'e'. */
    @Operation(summary = "Cerca un film per ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Film trovato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Movie.class))),
            @ApiResponse(responseCode = "404", description = "Film non trovato", content = @Content),
            @ApiResponse(responseCode = "400", description = "L'ID non e' un numero", content = @Content)
    })
    @GetMapping("/{id}")
    public Movie findById(
            @Parameter(description = "Identificativo del film", example = "1")
            @PathVariable Long id) {
        return service.findById(id);
    }

    /**
     * inserimento
     */
    @Operation(summary = "Crea un film",
            description = "Il titolo deve essere unico nel catalogo. "
                    + "Un id eventualmente inviato viene ignorato.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Creato, con header Location",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Movie.class))),
            @ApiResponse(responseCode = "400", description = "Titolo mancante o durata non positiva",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Titolo gia' presente in catalogo",
                    content = @Content)
    })
    @PostMapping
    public ResponseEntity<Movie> crea(@RequestBody Movie richiesta) {
        Movie creato = service.create(richiesta.getTitle(), richiesta.getDurationMinutes());

        // 201 con Location: il client sa dove e' finita la risorsa che ha
        // creato, senza doverla cercare.
        return ResponseEntity.created(URI.create("/movies/" + creato.getId()))
                .body(creato);
    }

    /**
     * PUT /movies/{id} — il film da aggiornare si indica NELL'URL.
     *
     * PUT: si inviano TUTTI i campi modificabili, non solo quelli cambiati.
     *
     * L'id sta nel path e non nel body perche' in HTTP il metodo dice cosa
     * fare e l'URL dice A COSA: "PUT /movies" significherebbe "sostituisci
     * l'intera collezione dei film". Con l'id nel path, cache, proxy e log
     * sanno quale risorsa e' cambiata senza dover aprire il corpo della
     * richiesta. E' anche la stessa forma di PUT /shows/{id}: due risorse
     * dello stesso servizio non si aggiornano in due modi diversi.
     */
    @Operation(summary = "Aggiorna titolo e durata di un film",
            description = "Il film si indica nell'URL. Se il corpo contiene anche "
                    + "un id, deve coincidere con quello del path.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggiornato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Movie.class))),
            @ApiResponse(responseCode = "400", description = "Dati non validi o id discordante",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Film non trovato", content = @Content),
            @ApiResponse(responseCode = "409", description = "Il titolo appartiene gia' a un altro film",
                    content = @Content)
    })
    @PutMapping("/{id}")
    public Movie update(
            @Parameter(description = "Identificativo del film", example = "1")
            @PathVariable Long id,
            @RequestBody Movie richiesta) {

        // L'id puo' comparire in due posti: qui si dichiara chi comanda.
        //
        // Vale l'URL. Il body puo' ometterlo (ed e' il caso normale) oppure
        // ripeterlo identico, ma se dice un id DIVERSO la richiesta e'
        // contraddittoria e va rifiutata: indovinare quale dei due intendeva
        // il client significa aggiornare il film sbagliato senza dirglielo.
        if (richiesta.getId() != null && !richiesta.getId().equals(id)) {
            throw new IllegalArgumentException(
                    "L'id nel corpo (" + richiesta.getId() + ") non coincide con quello "
                            + "nell'URL (" + id + ")");
        }
        return service.update(id, richiesta.getTitle(), richiesta.getDurationMinutes());
    }

    @Operation(summary = "Elimina un film",
            description = "Solo se non e' citato da nessuno spettacolo.")
    @ApiResponses({
            // 204 e non 200: e' andata bene e non c'e' niente da dire
            @ApiResponse(responseCode = "204", description = "Eliminato, nessun corpo"),
            @ApiResponse(responseCode = "404", description = "Film non trovato", content = @Content),
            @ApiResponse(responseCode = "409", description = "Il film e' ancora in programmazione",
                    content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identificativo del film", example = "1")
            @PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Traduce le eccezioni di dominio in status HTTP. Senza, sarebbero tutte
     * 500: "non l'ho trovato", "ce l'ho gia'" e "mi sono rotto" sono tre cose
     * diverse, e il client deve poterle distinguere.
     *
     * @Hidden serve davvero: senza, springdoc DEDUCE questi status e li
     * appiccica a TUTTI i metodi del controller, GET /movies compreso — che
     * non puo' rispondere 404. Con @Hidden restano solo gli @ApiResponse
     * dichiarati a mano, che sono precisi.
     */
    @Hidden
    @ExceptionHandler(MovieNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(MovieNotFoundException e) {
        return e.getMessage();
    }

    /**
     * 409 e non 400: la richiesta e' scritta bene, e' lo STATO del catalogo a
     * renderla impossibile. La stessa identica richiesta, mandata prima che
     * quel titolo esistesse, sarebbe andata a buon fine.
     */
    @Hidden
    @ExceptionHandler(MovieTitleAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String titoloDuplicato(MovieTitleAlreadyExistsException e) {
        return e.getMessage();
    }

    @Hidden
    @ExceptionHandler(MovieInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String inProgrammazione(MovieInUseException e) {
        return e.getMessage();
    }

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
     *
     * Nota: questo handler e' identico a quello di ShowController, e cosi'
     * sono notFound e badRequest. E' il segnale che serve un
     * @RestControllerAdvice — materiale del G4, insieme ai DTO.
     */
    @Hidden
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflittoDiConcorrenza(OptimisticLockingFailureException e) {
        return "Qualcun altro ha modificato il film mentre completavi "
                + "l'operazione. Riprova.";
    }
}
