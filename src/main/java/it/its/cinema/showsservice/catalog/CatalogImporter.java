package it.its.cinema.showsservice.catalog;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * PASSO 4.4 — IMPORTARE IL CATALOGO DA UN JSON ESTERNO, ALL'AVVIO.
 *
 * Fino a ieri i film di esempio arrivavano dalla migrazione V2, cioe' da un
 * INSERT dentro lo schema. Sbagliato come abitudine: una migrazione descrive la
 * forma del database, non il suo contenuto. I dati vanno caricati da fuori, da
 * un file che si puo' sostituire senza toccare il codice ne' la storia di
 * Flyway.
 *
 * ApplicationRunner gira UNA volta, dopo che il contesto e' pronto e prima che
 * il servizio cominci a servire richieste. Il che vuol dire anche: se questo
 * metodo lancia, l'applicazione non parte. E' voluto — un catalogo che non si
 * riesce a leggere e' un problema da vedere subito, non alla prima GET.
 *
 * ---------------------------------------------------------------------------
 * PASSO 4.3 — ATTENZIONE ALL'IMPORT DI ObjectMapper. E' LA TRAPPOLA DI OGGI.
 *
 *     import tools.jackson.databind.ObjectMapper;           // Jackson 3  <- questo
 *     import com.fasterxml.jackson.databind.ObjectMapper;   // Jackson 2  <- NO
 *
 * Spring Boot 4 usa Jackson 3, dove il package e' cambiato da
 * com.fasterxml.jackson a tools.jackson. Ma Jackson 2 resta sul classpath,
 * trascinato da altre librerie (in questo progetto ce lo porta springdoc):
 * l'IDE propone DUE ObjectMapper con lo stesso nome, e quello sbagliato
 * compila benissimo. Il guasto arriva all'avvio, come fallimento
 * dell'iniezione, e il messaggio non nomina l'import.
 *
 * Le ANNOTAZIONI invece non si sono spostate: @JsonProperty e compagnia
 * restano in com.fasterxml.jackson.annotation anche con Jackson 3.
 * ---------------------------------------------------------------------------
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogImporter implements ApplicationRunner {

    /** L'ObjectMapper di Jackson 3, quello che Boot ha gia' configurato. */
    private final ObjectMapper objectMapper;

    private final MovieRepository movieRepository;

    /**
     * Il percorso e' configurabile, e il valore di default e' il file nel jar.
     *
     * "classpath:catalog.json" in sviluppo, "file:/etc/cinema/catalog.json" in
     * un container: cambia una riga di application.yaml o una variabile
     * d'ambiente, non il codice. Resource astrae le due cose, ed e' il motivo
     * per cui il tipo non e' String ne' Path.
     */
    @Value("${cinema.catalog-file:classpath:catalog.json}")
    private Resource catalogo;

    /**
     * L'IMPORT DEVE ESSERE IDEMPOTENTE: al secondo avvio non duplica niente.
     *
     * Non e' un dettaglio di eleganza. Questo metodo gira a OGNI avvio, e un
     * servizio in produzione riparte a ogni deploy, a ogni riavvio del nodo, a
     * ogni scalata di replica. Senza il controllo, il catalogo raddoppierebbe
     * ogni volta — o meglio, ci proverebbe: sbatterebbe contro il vincolo
     * uk_movies_title e impedirebbe l'avvio.
     *
     * Il controllo si fa con UNA query che carica i titoli gia' presenti, non
     * con existsByTitle dentro al ciclo: quello sarebbe un N+1 scritto a mano,
     * ed e' esattamente il problema del passo 3.3.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!catalogo.exists()) {
            // Un catalogo assente non e' un errore: in un ambiente dove i film
            // arrivano da altrove, il file semplicemente non c'e'.
            log.info("nessun catalogo da importare: {} non esiste", catalogo);
            return;
        }

        List<FilmDelCatalogo> daImportare;
        try (InputStream stream = catalogo.getInputStream()) {
            daImportare = objectMapper.readValue(stream, new TypeReference<List<FilmDelCatalogo>>() {});
        }

        Set<String> giaPresenti = movieRepository.findAll().stream()
                .map(Movie::getTitle)
                .collect(Collectors.toSet());

        List<Movie> nuovi = daImportare.stream()
                .filter(f -> !giaPresenti.contains(f.title()))
                .map(f -> new Movie(f.title(), f.durationMinutes()))
                .toList();

        if (nuovi.isEmpty()) {
            log.info("catalogo gia' allineato: {} film nel file, nessuno nuovo", daImportare.size());
            return;
        }

        movieRepository.saveAll(nuovi);
        log.info("importati {} film nuovi da {} ({} erano gia' in catalogo)",
                nuovi.size(), catalogo.getFilename(), daImportare.size() - nuovi.size());
    }

    /**
     * La forma di una riga del file, e niente altro.
     *
     * Un record locale al package dell'importatore, non l'entita' Movie: il
     * formato del file e' un contratto con chi lo produce, e non deve
     * trascinarsi dietro id, version e annotazioni JPA. E' la stessa ragione
     * per cui esistono i DTO del passo 4.1, applicata all'ingresso da file
     * invece che da HTTP.
     */
    record FilmDelCatalogo(String title, int durationMinutes) {
    }
}
