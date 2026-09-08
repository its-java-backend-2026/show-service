package it.its.cinema.showsservice.domain;

public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(Long id) {
        super("Film non trovato: " + id);
    }
}
