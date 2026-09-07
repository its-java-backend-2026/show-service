package it.its.cinema.showsservice.domain;

public class NotEnoughSeatsException extends RuntimeException {

    public NotEnoughSeatsException(Long showId, int richiesti, int disponibili) {
        super("Posti insufficienti per lo spettacolo " + showId
                + ": richiesti " + richiesti + ", disponibili " + disponibili);
    }
}
