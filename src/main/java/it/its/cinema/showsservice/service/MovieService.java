package it.its.cinema.showsservice.service;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.MovieInUseException;
import it.its.cinema.showsservice.domain.MovieNotFoundException;
import it.its.cinema.showsservice.domain.MovieTitleAlreadyExistsException;
import it.its.cinema.showsservice.repository.MovieRepository;
import it.its.cinema.showsservice.repository.ShowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Il catalogo dei film:
 *
 * PASSO 3.6 — LE TRANSAZIONI STANNO QUI, come in ShowService.
 *
 * Non nel controller (resterebbero aperte durante la serializzazione HTTP) e
 * non nel repository (un caso d'uso che tocca due tabelle deve riuscire o
 * fallire tutto insieme, e questo lo sa solo il service).
 *
 * Due cose da guardare qui sotto, che su ShowService non si vedevano:
 *
 *  1. deleteById() fa TRE operazioni su DUE tabelle. Senza @Transactional
 *     sono tre transazioni distinte, e fra il controllo "non e' in
 *     programmazione" e la cancellazione qualcuno puo' creare uno spettacolo
 *     che punta a questo film. E' il motivo per cui la transazione sta qui.
 *
 *  2. update() chiama findById(), che e' un metodo DELLA STESSA CLASSE: e'
 *     self-invocation, non passa dal proxy di Spring e la sua
 *     @Transactional(readOnly = true) NON viene applicata. Vale quella,
 *     scrivibile, di update() — che e' quello che serve, ma per fortuna e non
 *     per progetto. Togliendo @Transactional da update(), il metodo girerebbe
 *     su tre transazioni separate senza un solo warning.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

    private final MovieRepository repository;
    private final ShowRepository showRepository;

    /**
     * PASSO 3.4 — l'elenco e' paginato.
     *
     * readOnly = true non e' cosmetico: Hibernate salta il dirty checking e il
     * database puo' instradare la query su una replica di lettura.
     */
    @Transactional(readOnly = true)
    public Page<Movie> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /** Il film richiesto, oppure MovieNotFoundException. */
    @Transactional(readOnly = true)
    public Movie findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));
    }

    /**
     * Ricerca per titolo, parziale e case insensitive.
     *
     * Nessun risultato NON e' un errore
     */
    @Transactional(readOnly = true)
    public Page<Movie> searchByTitle(String frammento, Pageable pageable) {
        if (frammento == null || frammento.isBlank()) {
            throw new IllegalArgumentException("Il titolo da cercare e' obbligatorio");
        }
        Page<Movie> trovati = repository.findByTitleContainingIgnoreCase(frammento.trim(), pageable);
        // getTotalElements e' il totale dei risultati, non quelli di questa
        // pagina: e' il numero che interessa in un log di ricerca.
        log.info("ricerca film per titolo '{}': {} risultati", frammento, trovati.getTotalElements());
        return trovati;
    }

    /**
     * Aggiunge un film al catalogo.
     */
    @Transactional
    public Movie create(String title, int durationMinutes) {
        String titolo = validate(title, durationMinutes);

        // Il vincolo uk_movies_title bloccherebbe comunque l'inserimento, ma
        // con una violazione di constraint -> 500 "mi sono rotto". Chiedendolo
        // prima, il client legge 409 "esiste gia'", che e' la verita'.
        //
        // Resta una finestra di gara: due richieste simultanee passano
        // entrambe il controllo e una delle due sbatte comunque sul vincolo.
        // E' accettabile proprio perche' il database regge lo stesso: il
        // controllo qui migliora il messaggio, non garantisce l'unicita'.
        if (repository.existsByTitle(titolo)) {
            throw new MovieTitleAlreadyExistsException(titolo);
        }

        Movie creato = repository.save(new Movie(titolo, durationMinutes));
        log.info("creato il film {} ({}, {} minuti)", creato.getId(), titolo, durationMinutes);
        return creato;
    }

    /**
     * Aggiorna titolo e durata. 
     *
     * Si carica prima il film esistente e se ne cambiano i campi
     */
    @Transactional
    public Movie update(Long id, String title, int durationMinutes) {
        Movie movie = findById(id);
        String titolo = validate(title, durationMinutes);

        // Il titolo puo' restare lo stesso (si sta cambiando solo la durata):
        // e' un conflitto solo se appartiene a un ALTRO film.
        repository.findByTitle(titolo)
                .filter(altro -> !altro.getId().equals(id))
                .ifPresent(altro -> {
                    throw new MovieTitleAlreadyExistsException(titolo);
                });

        movie.setTitle(titolo);
        movie.setDurationMinutes(durationMinutes);
        log.info("aggiornato il film {}", id);
        // Con la transazione aperta il save() e' ridondante: movie e' gestito
        // dal persistence context e il dirty checking scrive comunque al
        // commit. Si tiene perche' rende esplicito l'intento e perche' e' il
        // save() a restituire l'istanza da serializzare.
        return repository.save(movie);
    }

    /** 404 se non esiste, 409 se e' ancora in programmazione. */
    @Transactional
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new MovieNotFoundException(id);
        }
        if (showRepository.existsByMovieId(id)) {
            throw new MovieInUseException(id);
        }
        repository.deleteById(id);
        log.info("eliminato il film {}", id);
    }

    /**
     * Le validazioni del film.
     * @return il titolo ripulito dagli spazi ai bordi
     */
    private String validate(String title, int durationMinutes) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Il titolo e' obbligatorio");
        }
        String titolo = title.trim();
        if (titolo.length() > 200) {
            // combacia con VARCHAR(200) della migrazione V1: senza questo
            // controllo il database taglierebbe la richiesta con un errore
            // molto meno leggibile
            throw new IllegalArgumentException("Il titolo non puo' superare i 200 caratteri");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("La durata deve essere positiva");
        }
        return titolo;
    }
}
