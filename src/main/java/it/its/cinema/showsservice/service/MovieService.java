package it.its.cinema.showsservice.service;

import java.util.List;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.MovieInUseException;
import it.its.cinema.showsservice.domain.MovieNotFoundException;
import it.its.cinema.showsservice.domain.MovieTitleAlreadyExistsException;
import it.its.cinema.showsservice.repository.MovieRepository;
import it.its.cinema.showsservice.repository.ShowRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Il catalogo dei film:
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

    private final MovieRepository repository;
    private final ShowRepository showRepository;

    public List<Movie> findAll() {
        return repository.findAll();
    }

    /** Il film richiesto, oppure MovieNotFoundException. */
    public Movie findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));
    }

    /**
     * Ricerca per titolo, parziale e case insensitive.
     *
     * Nessun risultato NON e' un errore
     */
    public List<Movie> searchByTitle(String frammento) {
        if (frammento == null || frammento.isBlank()) {
            throw new IllegalArgumentException("Il titolo da cercare e' obbligatorio");
        }
        List<Movie> trovati = repository.findByTitleContainingIgnoreCase(frammento.trim());
        log.info("ricerca film per titolo '{}': {} risultati", frammento, trovati.size());
        return trovati;
    }

    /**
     * Aggiunge un film al catalogo.
     */
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
        return repository.save(movie);
    }

    /** 404 se non esiste, 409 se e' ancora in programmazione. */
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
