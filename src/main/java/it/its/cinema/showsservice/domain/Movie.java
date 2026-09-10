package it.its.cinema.showsservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {
    @Id
    // IDENTITY: l'id lo genera il database con BIGSERIAL.
    // Non AUTO, che su PostgreSQL sceglie una tabella di sequenze condivisa.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false,name = "minutes")
    private int durationMinutes;

    /**
     * PASSO 3.7 — LOCK OTTIMISTICO, anche sul catalogo.
     *
     * Sul film non si perdono poltrone, si perdono modifiche: due redattori
     * aprono lo stesso film, uno corregge il titolo, l'altro la durata, e chi
     * salva per secondo riscrive anche il campo che non ha toccato. E' lo
     * stesso "lost update" degli spettacoli, con conseguenze meno vistose.
     *
     * Con @Version ogni UPDATE porta in coda "AND version = <letta>": chi
     * arriva secondo aggiorna zero righe e Hibernate solleva
     * OptimisticLockingFailureException, che il controller traduce in 409.
     *
     * ATTENZIONE: version NON compare nei costruttori qui sotto. Lo gestisce
     * Hibernate, e ogni costruttore con argomenti in piu' e' un'altra
     * occasione per il problema Jackson descritto sopra.
     */
    @Version
    private Long version;

    /**
     * PASSO 4.1 — QUI C'ERANO DUE @JsonCreator(mode = DISABLED), E OGGI NON CI SONO PIU'.
     *
     * Servivano a difendersi da Jackson: in Boot 4 / Jackson 3 un costruttore
     * con argomenti viene promosso automaticamente a "creator", e da quel
     * momento Jackson pretende TUTTI i suoi parametri. Un POST che non mandava
     * "minutes" falliva con 400:
     *     JSON parse error: Cannot map `null` into type `int`
     * Mode.DISABLED spegneva la promozione e riportava Jackson a
     * @NoArgsConstructor + setter.
     *
     * Sono sparite perche' e' sparito il problema: Jackson non tocca piu'
     * questa classe. In ingresso arriva MovieRequest, in uscita esce
     * MovieResponse, e l'entita' non attraversa piu' il confine HTTP.
     *
     * E' la ragione meno raccontata per cui esistono i DTO: senza, il dominio
     * finisce per portarsi addosso le annotazioni di DUE librerie che non si
     * parlano fra loro — JPA per il database, Jackson per il JSON — e ogni
     * modifica deve accontentarle entrambe. I costruttori qui sotto ora
     * rispondono solo a Hibernate e a chi scrive codice Java.
     */
    public Movie(String title, int durationMinutes) {
        this.title = title;
        this.durationMinutes = durationMinutes;
    }

    /** Il completo: lo usano i dati di esempio e i test, che hanno anche l'id. */
    public Movie(Long id, String title, int durationMinutes) {
        this.id = id;
        this.title = title;
        this.durationMinutes = durationMinutes;
    }
}
