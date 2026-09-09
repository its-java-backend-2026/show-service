package it.its.cinema.showsservice.repository;

import it.its.cinema.showsservice.domain.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository 
public interface MovieRepository extends JpaRepository<Movie, Long> {

    /**
     * Ricerca per titolo, PARZIALE e senza distinzione fra maiuscole e
     * minuscole.
     *
     * PASSO 3.4 — da oggi paginata: prima tornava List<Movie>, cioe' tutti i
     * risultati sempre. Con "e" in un catalogo vero sono decine di migliaia di
     * righe caricate in memoria per mostrarne venti.
     *
     * Non serve il trucco "findAllBy" di ShowRepository: quello nasceva solo
     * perche' @EntityGraph non fa presa su un metodo ereditato, e qui non c'e'
     * nessun @EntityGraph da applicare (Movie non ha relazioni da caricare).
     * L'elenco completo usa direttamente findAll(Pageable), che JpaRepository
     * offre gia'.
     *
     * NOTA sull'indice: questa query diventa
     *     lower(title) like '%frammento%'
     * e con il wildcard iniziale nessun indice btree e' utilizzabile. Vedere
     * il commento nella migrazione V4 e provare con EXPLAIN.
     */
    Page<Movie> findByTitleContainingIgnoreCase(String frammento, Pageable pageable);

    /**
     * Ricerca ESATTA
     */
    Optional<Movie> findByTitle(String title);

    boolean existsByTitle(String title);
}
