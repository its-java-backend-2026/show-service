package it.its.cinema.showsservice.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
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
     * ATTENZIONE: basta UN solo costruttore con argomenti non disabilitato
     * perche' Jackson lo promuova, e il 400 torni. Per questo il costruttore
     * completo qui sotto e' scritto a mano e disabilitato anche lui, invece di
     * arrivare da @AllArgsConstructor: un costruttore generato da Lombok non
     * si puo' annotare.
     *
     * E' anche una buona ragione per NON accettare entity in ingresso:
     * al G4 arrivano i DTO e queste annotazioni spariscono dal dominio.
     */
    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public Movie(String title, int durationMinutes) {
        this.title = title;
        this.durationMinutes = durationMinutes;
    }

    /** Il completo: lo usano i dati di esempio e i test, che hanno anche l'id. */
    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public Movie(Long id, String title, int durationMinutes) {
        this.id = id;
        this.title = title;
        this.durationMinutes = durationMinutes;
    }
}
