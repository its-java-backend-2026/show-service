package it.its.cinema.showsservice.web;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import it.its.cinema.showsservice.catalog.EsitoImport;
import it.its.cinema.showsservice.catalog.RemoteCatalogImporter;
import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.service.MovieService;
import it.its.cinema.showsservice.web.dto.ImportResponse;
import it.its.cinema.showsservice.web.dto.MovieRequest;
import it.its.cinema.showsservice.web.dto.MovieResponse;
import it.its.cinema.showsservice.web.mapper.MovieMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
 * Il catalogo dei film via HTTP.
 *
 * PASSO 4.1 — entrano ed escono DTO: MovieRequest e MovieResponse.
 * PASSO 4.6 — gli @ExceptionHandler che stavano qui sono in GestoreErrori.
 */
@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService service;
    private final MovieMapper mapper;

    /** PASSO 6.8b — il catalogo del fornitore esterno, via Feign. */
    private final RemoteCatalogImporter catalogoRemoto;

    /**
     * GET /movies-> tutto il catalogo
     * GET /movies?title=du -> solo i film il cui titolo contiene "du"
     *
     * PASSO 3.4 — paginazione e ordinamento, come su GET /shows.
     * Provare:  GET /movies?page=0&size=5&sort=title,desc
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
                            schema = @Schema(implementation = MovieResponse.class))),
            @ApiResponse(responseCode = "400", description = "Il parametro title e' presente ma vuoto",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public Page<MovieResponse> findAll(
            @Parameter(description = "Frammento di titolo da cercare", example = "dune")
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return mapper.toResponse(title == null
                ? service.findAll(pageable)
                : service.searchByTitle(title, pageable));
    }

    /** GET /movies/{id} — 200 se c'e', 404 se non c'e'. */
    @Operation(summary = "Cerca un film per ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Film trovato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MovieResponse.class))),
            @ApiResponse(responseCode = "404", description = "Film non trovato",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "400", description = "L'ID non e' un numero",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public MovieResponse findById(
            @Parameter(description = "Identificativo del film", example = "1")
            @PathVariable Long id) {
        return mapper.toResponse(service.findById(id));
    }

    /**
     * inserimento
     *
     * PASSO 4.1 — l'id non viene piu' "ignorato": MovieRequest non ce l'ha.
     */
    @Operation(summary = "Crea un film",
            description = "Il titolo deve essere unico nel catalogo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Creato, con header Location",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MovieResponse.class))),
            @ApiResponse(responseCode = "400", description = "Titolo mancante o durata non positiva",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Titolo gia' presente in catalogo",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<MovieResponse> crea(@Valid @RequestBody MovieRequest richiesta) {
        Movie creato = service.create(richiesta.title(), richiesta.durationMinutes());

        // 201 con Location: il client sa dove e' finita la risorsa che ha
        // creato, senza doverla cercare.
        return ResponseEntity.created(URI.create("/movies/" + creato.getId()))
                .body(mapper.toResponse(creato));
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
     * richiesta.
     *
     * PASSO 4.1 — con MovieRequest il controllo "id del body diverso da quello
     * dell'URL -> 400" e' sparito, e non e' una dimenticanza: quel controllo
     * esisteva per risolvere una contraddizione che ora non si puo' piu'
     * scrivere. Un id nel corpo non viene rifiutato, viene semplicemente
     * ignorato come qualunque campo sconosciuto.
     */
    @Operation(summary = "Aggiorna titolo e durata di un film",
            description = "Il film si indica nell'URL. Il corpo contiene solo "
                    + "titolo e durata.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggiornato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MovieResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dati non validi",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Film non trovato",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Il titolo appartiene gia' a un altro film",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}")
    public MovieResponse update(
            @Parameter(description = "Identificativo del film", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody MovieRequest richiesta) {
        return mapper.toResponse(
                service.update(id, richiesta.title(), richiesta.durationMinutes()));
    }

    @Operation(summary = "Elimina un film",
            description = "Solo se non e' citato da nessuno spettacolo.")
    @ApiResponses({
            // 204 e non 200: e' andata bene e non c'e' niente da dire
            @ApiResponse(responseCode = "204", description = "Eliminato, nessun corpo"),
            @ApiResponse(responseCode = "404", description = "Film non trovato",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Il film e' ancora in programmazione",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identificativo del film", example = "1")
            @PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PASSO 6.8b — POST /movies/importa-da-fornitore
     *
     * Scarica il catalogo di un fornitore esterno con Feign e importa i film
     * che non abbiamo. E' l'unica rotta del progetto che, per rispondere,
     * chiama un ALTRO servizio: da qui in poi (G6) sara' la normalita'.
     *
     * POST e non GET: la chiamata cambia lo stato del nostro catalogo. Non e'
     * idempotente nel senso di HTTP — la seconda volta importa zero film, non
     * perche' la rotta sia idempotente ma perche' lo e' la regola di fusione,
     * che e' una garanzia piu' forte e ce la siamo scritta noi.
     *
     * Chi risponde 503: GestoreErrori, quando il fornitore non c'e'.
     */
    @Operation(summary = "Importa il catalogo da un fornitore esterno",
            description = "Scarica il catalogo remoto (client Feign) e inserisce "
                    + "solo i film non ancora presenti. Rieseguirlo non duplica "
                    + "niente: la seconda chiamata importa zero film.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Import eseguito, anche con zero film nuovi",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImportResponse.class))),
            @ApiResponse(responseCode = "503", description = "Il fornitore non ha risposto: non e' un errore nostro",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/importa-da-fornitore")
    public ImportResponse importaDaFornitore() {
        EsitoImport esito = catalogoRemoto.importaDalFornitore();
        return new ImportResponse(esito.importati(), esito.giaPresenti(),
                esito.totaleNellaSorgente());
    }
}
