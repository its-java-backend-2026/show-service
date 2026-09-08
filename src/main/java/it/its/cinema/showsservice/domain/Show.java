package it.its.cinema.showsservice.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shows")
@Getter
@Setter
@NoArgsConstructor
public class Show {

    /** Dalle 20:00 in poi lo spettacolo e' serale. Dal G6 pricing-service ci mette un supplemento. */
    public static final int PRIMA_ORA_SERALE = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * La relazione verso il film, con la foreign key movie_id.
     *
     * NOTA: @ManyToOne di default e' EAGER, quindi ogni spettacolo si porta
     * dietro il suo film. Funziona, ma su GET /shows genera una query per lo
     * elenco piu' una per ogni film: e' il problema N+1.
     * Al G3 lo si guarda nei log e lo si risolve. Oggi si lascia com'e',
     * di proposito: prima si vede il problema, poi si impara la soluzione.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * BigDecimal, MAI double: in virgola mobile 0.1 + 0.2 non fa 0.3.
     * precision/scale devono combaciare con NUMERIC(8,2) della migrazione,
     * altrimenti ddl-auto: validate blocca l'avvio.
     */
    @Column(name = "base_price", nullable = false, precision = 8, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    /**
     * L'id non e' piu' un parametro: lo assegna il database.
     * availableSeats non lo e' mai stato: non e' un dato di ingresso,
     * e' una conseguenza.
     */
    /**
     * ATTENZIONE Boot 4 / Jackson 3 — questa riga sembra inutile e non lo e'.
     *
     * Jackson 3 promuove automaticamente un costruttore con argomenti a
     * "creator" e da quel momento pretende TUTTI i suoi parametri. Un POST con
     * {"movie": {"id": 3}} fallisce allora con 400:
     *     JSON parse error: Cannot map `null` into type `int`
     * perche' "minutes" non e' stato inviato. In Jackson 2 non succedeva:
     * usava il costruttore vuoto piu' i setter.
     *
     * Mode.DISABLED dice a Jackson di ignorare questo costruttore e tornare a
     * @NoArgsConstructor + setter. Hibernate continua a usarlo normalmente.
     *
     * ATTENZIONE: perche' funzioni non ci deve essere NESSUN altro costruttore
     * con argomenti. @AllArgsConstructor ne generava uno, non annotabile, che
     * Jackson promuoveva al posto di questo: era il 400 su POST /shows (che
     * non manda "id") e su PUT /shows/{id} (che manda solo due campi).
     *
     * E' anche una buona ragione per NON accettare entity in ingresso:
     * al G4 arrivano i DTO e queste annotazioni spariscono dal dominio.
     */
    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
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
