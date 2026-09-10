package it.its.cinema.showsservice.domain;

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
    /**
     * PASSO 3.1 — LAZY ESPLICITO.
     *
     * @ManyToOne di default e' EAGER: e' la trappola meglio nascosta di JPA.
     * Ieri ogni spettacolo si tirava dietro il suo film sempre, anche quando
     * non serviva, e su GET /shows generava una query per l'elenco piu' una
     * per ogni film distinto: il problema N+1.
     *
     * Con LAZY il film si carica solo se qualcuno lo chiede. Ma attenzione:
     * con open-in-view: false, "qualcuno lo chiede" fuori dalla transazione
     * significa LazyInitializationException. Per questo il repository ha
     * @EntityGraph: il film si carica NELLA STESSA query, quando serve.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
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
     * PASSO 3.7 — LOCK OTTIMISTICO.
     *
     * Il problema, senza: due clienti, ultimi due posti. Entrambi leggono
     * availableSeats = 2, entrambi calcolano 0, entrambi scrivono 0.
     * Quattro biglietti venduti, due poltrone, e il database e' perfettamente
     * coerente: nessun vincolo e' stato violato. E' il "lost update".
     *
     * Con @Version, ogni UPDATE porta in coda "AND version = <letta>".
     * Chi arriva secondo aggiorna zero righe, e Hibernate lo traduce in
     * OptimisticLockingFailureException. Nessun lock tenuto, nessuna attesa.
     */
    @Version
    private Long version;

    /**
     * L'id non e' piu' un parametro: lo assegna il database.
     * availableSeats non lo e' mai stato: non e' un dato di ingresso,
     * e' una conseguenza.
     */
    /**
     * PASSO 4.1 — QUI C'ERA UN @JsonCreator(mode = DISABLED), E OGGI NON C'E' PIU'.
     *
     * Serviva a impedire a Jackson 3 di promuovere questo costruttore a
     * "creator" e pretendere tutti i suoi parametri a ogni POST. Ora Jackson
     * non vede piu' questa classe: in ingresso c'e' CreateShowRequest, in
     * uscita ShowResponse, e l'entita' non attraversa il confine HTTP.
     *
     * Il costruttore resta com'e', e le sue validazioni pure. Non sono un
     * doppione di quelle del DTO: il DTO difende il confine HTTP, questo
     * difende l'oggetto da CHIUNQUE lo costruisca — un importatore, un test,
     * un service futuro. Chi entra da una porta diversa da HTTP non incontra
     * nessun @Valid.
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
