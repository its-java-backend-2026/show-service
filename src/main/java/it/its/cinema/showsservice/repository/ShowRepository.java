package it.its.cinema.showsservice.repository;

import it.its.cinema.showsservice.domain.Show;

import java.util.List;
import java.util.Optional;

public interface ShowRepository {
    Show save(Show show);

    Optional<Show> findById(Long id);

    List<Show> findAll();

    void deleteById(Long id);

    boolean existsById(Long id);
}