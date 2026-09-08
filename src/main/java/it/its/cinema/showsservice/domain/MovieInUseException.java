package it.its.cinema.showsservice.domain;

/**
 * Un film citato da almeno uno spettacolo non si cancella.
 *
 * Non e' un capriccio del database: cancellarlo lascerebbe in programmazione
 * spettacoli senza film. Chi vuole davvero rimuoverlo elimina prima i suoi
 * spettacoli, e cosi' se ne assume la responsabilita' esplicitamente.
 */
public class MovieInUseException extends RuntimeException {

    public MovieInUseException(Long id) {
        super("Il film " + id + " e' in programmazione: elimina prima i suoi spettacoli");
    }
}
