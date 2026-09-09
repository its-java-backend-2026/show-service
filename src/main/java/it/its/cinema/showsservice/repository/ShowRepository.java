package it.its.cinema.showsservice.repository;

import it.its.cinema.showsservice.domain.Show;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository 
public interface ShowRepository extends JpaRepository<Show, Long> {

    /**
     * Serve a MovieService prima di cancellare un film: la colonna
     * shows.movie_id ha una foreign key, quindi il database rifiuterebbe
     * comunque la DELETE. Chiedendolo prima, il client legge "il film e' in
     * programmazione" (409) invece di un errore interno (500).
     */
    boolean existsByMovieId(Long movieId);

    /**
     * PASSO 3.3 — LA CURA DEL PROBLEMA N+1.
     *
     * @EntityGraph dice a Hibernate di caricare il film NELLA STESSA query,
     * con una join. Senza, findAll() su venti spettacoli produce:
     *     1 query per l'elenco  +  1 per ogni film distinto
     * Con, ne produce una sola. Si conta nei log (passo 3.2).
     *
     * Il nome e' findAllBy e non findAll: findAll e' gia' definito da
     * JpaRepository e l'@EntityGraph su un metodo ereditato non si applica.
     * Il "By" senza criteri significa "tutti", ed e' il modo di Spring Data
     * per dichiarare un metodo nuovo su cui l'annotazione fa presa.
     */
    @EntityGraph(attributePaths = "movie")
    Page<Show> findAllBy(Pageable pageable);

    /** Anche la lettura singola carica il film: il JSON in uscita lo contiene.
     * findWithMovieById sfrutta una caratteristica di Spring Data JPA: il testo tra find e
     * By (se non corrisponde a un campo dell'entità) viene ignorato ai fini della query —
     * serve solo come descrizione leggibile per chi legge il codice.
     * Quindi Spring Data lo interpreta come findById(Long id): cerca uno Show per id e
     * ritorna Optional<Show>.*/
    @EntityGraph(attributePaths = "movie")
    Optional<Show> findWithMovieById(Long id);

    /**
     * PASSO 3.5 — una query di dominio, con l'indice idx_shows_movie_start
     * a supporto (migrazione V3).
     *
     * "join fetch" fa lo stesso lavoro di @EntityGraph: carica il film nella
     * stessa query. Sono due modi per la stessa cosa, ed e' bene vederli
     * entrambi almeno una volta.
     */
    @Query("""
            select s from Show s
            join fetch s.movie m
            where m.id = :movieId
              and s.startTime between :da and :a
            order by s.startTime
            """)
    List<Show> findByFilmAndInterval(@Param("movieId") Long movieId,
                                       @Param("da") LocalDateTime da,
                                       @Param("a") LocalDateTime a);
}
