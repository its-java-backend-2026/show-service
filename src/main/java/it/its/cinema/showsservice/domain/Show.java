package it.its.cinema.showsservice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Show {
    private Long id;
    private Movie movie;
    private LocalDateTime startTime;
    private  BigDecimal basePrice;
    private  int totalSeats;
    private int availableSeats;
    /** Dalle 20:00 in poi lo spettacolo e' serale. Dal G6 pricing-service ci mette un supplemento. */
    public static final int PRIMA_ORA_SERALE = 20;

    /**
     * I controlli stanno nel costruttore: un oggetto non deve poter esistere
     * in uno stato incoerente. availableSeats non e' un parametro perche' non
     * e' un dato di ingresso, e' una conseguenza: all'inizio i posti liberi
     * sono tutti.
     */
    public Show(Long id, Movie movie, LocalDateTime startTime, BigDecimal basePrice, int totalSeats) {
        if (movie == null) {
            throw new IllegalArgumentException("Il film e' obbligatorio");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("L'orario di inizio e' obbligatorio");
        }
        if (basePrice == null || basePrice.signum() < 0) {
            throw new IllegalArgumentException("Il prezzo base non puo' essere negativo");
        }
        if (totalSeats <= 0) {
            throw new IllegalArgumentException("I posti totali devono essere positivi");
        }
        this.id = id;
        this.movie = movie;
        this.startTime = startTime;
        this.basePrice = basePrice;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
    }

    /**
     * Riserva dei posti.
     *
     * La regola sta nel dominio, non nel service e tantomeno nel controller:
     * chiunque abbia in mano uno Show non puo' portarlo in uno stato assurdo.
     */
    public void reserveSeats(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantita' deve essere positiva");
        }
        if (quantity > availableSeats) {
            throw new NotEnoughSeatsException(id, quantity, availableSeats);
        }
        availableSeats -= quantity;
    }

    /** Il Math.min garantisce che il valore non superi mai il tetto massimo, anche in
     * presenza di chiamate "in eccesso" */
    public void releaseSeats(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantita' deve essere positiva");
        }
        availableSeats = Math.min(totalSeats, availableSeats + quantity);
    }

    /** Spettacolo serale: dalle 20:00 in poi. */
    public boolean isEveningShow() {
        return startTime.getHour() >= PRIMA_ORA_SERALE;
    }
}
