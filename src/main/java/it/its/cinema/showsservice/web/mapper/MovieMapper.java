package it.its.cinema.showsservice.web.mapper;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.web.dto.MovieResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * PASSO 4.2 — il mapper del catalogo.
 *
 * Piu' semplice di ShowMapper perche' Movie non ha relazioni: nessun rischio di
 * LazyInitializationException, nessun campo da appiattire.
 */
@Component
public class MovieMapper {

    public MovieResponse toResponse(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDurationMinutes(),
                movie.getVersion());
    }

    public Page<MovieResponse> toResponse(Page<Movie> movies) {
        return movies.map(this::toResponse);
    }
}
