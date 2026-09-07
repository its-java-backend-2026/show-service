package it.its.cinema.showsservice.domain;

/**
 * Eccezione di dominio, non di web: non conosce HttpStatus.
 * E' il livello web a decidere che questa diventa un 404.
 */
public class ShowNotFoundException extends RuntimeException {

    public ShowNotFoundException(Long id) {
        super("Spettacolo non trovato: " + id);
    }
}
