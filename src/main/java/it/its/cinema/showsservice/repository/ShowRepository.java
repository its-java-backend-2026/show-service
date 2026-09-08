package it.its.cinema.showsservice.repository;

import it.its.cinema.showsservice.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository 
public interface ShowRepository extends JpaRepository<Show, Long> {

    /**
     * Serve a MovieService prima di cancellare un film: la colonna
     * shows.movie_id ha una foreign key, quindi il database rifiuterebbe
     * comunque la DELETE. Chiedendolo prima, il client legge "il film e' in
     * programmazione" (409) invece di un errore interno (500).
     */
    boolean existsByMovieId(Long movieId);
}
