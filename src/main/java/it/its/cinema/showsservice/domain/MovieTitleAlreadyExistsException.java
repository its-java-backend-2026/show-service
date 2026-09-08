package it.its.cinema.showsservice.domain;

/**
 * Il catalogo ha un vincolo UNIQUE sul titolo (uk_movies_title, migrazione V1).
 *
 * Eccezione di dominio, non di web: non conosce HttpStatus.
 * E' il livello web a decidere che questa diventa un 409.
 */
public class MovieTitleAlreadyExistsException extends RuntimeException {

    public MovieTitleAlreadyExistsException(String title) {
        super("Esiste gia' un film con questo titolo: " + title);
    }
}
