package it.its.cinema.showsservice.repository;

import it.its.cinema.showsservice.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository 
public interface MovieRepository extends JpaRepository<Movie, Long> {

    /**
     * Ricerca per titolo, PARZIALE e senza distinzione fra maiuscole e
     * minuscole
     */
    List<Movie> findByTitleContainingIgnoreCase(String frammento);

    /**
     * Ricerca ESATTA
     */
    Optional<Movie> findByTitle(String title);

    boolean existsByTitle(String title);
}
