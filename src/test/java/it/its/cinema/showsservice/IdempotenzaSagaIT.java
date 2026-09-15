package it.its.cinema.showsservice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.domain.TipoOperazione;
import it.its.cinema.showsservice.repository.MovieRepository;
import it.its.cinema.showsservice.repository.ShowOperationRepository;
import it.its.cinema.showsservice.repository.ShowRepository;
import it.its.cinema.showsservice.service.ShowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PASSO 8.3 — LA PROVA CHE RIPETERE NON FA DANNI.
 *
 * E' il test che chiude il buco lasciato aperto al G6 e dichiarato al G7:
 * fino a ieri, ritentare una riserva dopo un timeout scalava i posti una
 * seconda volta, e per questo POST /shows/{id}/reserve era l'unica chiamata
 * di booking-service senza @Retry.
 *
 * Serve un database vero: l'idempotenza qui non e' un if in Java, e' un
 * vincolo UNIQUE e una transazione che tiene insieme due scritture. Un mock
 * direbbe che va tutto bene.
 */
@SpringBootTest
@Testcontainers
@DisplayName("shows-service — l'idempotenza dei passi della saga (8.3)")
class IdempotenzaSagaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    ShowService service;

    @Autowired
    ShowRepository repository;

    @Autowired
    MovieRepository movieRepository;

    @Autowired
    ShowOperationRepository operazioni;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    private Long spettacoloCon(int posti) {
        return tx.execute(stato -> {
            Movie film = movieRepository.save(
                    new Movie("Film idempotenza " + System.nanoTime(), 100));
            Show show = new Show(null, film, LocalDateTime.of(2026, 11, 1, 21, 0),
                    new BigDecimal("10.00"), posti);
            return repository.save(show).getId();
        });
    }

    private int postiLiberi(Long showId) {
        return repository.findById(showId).orElseThrow().getAvailableSeats();
    }

    @Test
    @DisplayName("RISERVARE due volte con lo stesso sagaId scala i posti UNA volta")
    void riservaRipetutaNonScalaDueVolte() {
        Long showId = spettacoloCon(10);

        service.reserveSeats(showId, 2, "saga-riserva");
        assertThat(postiLiberi(showId)).isEqualTo(8);

        // E' il retry dopo un timeout: la richiesta era arrivata, si era
        // persa la risposta. Fino al G7 questa riga portava i posti a 6.
        service.reserveSeats(showId, 2, "saga-riserva");
        assertThat(postiLiberi(showId)).isEqualTo(8);
    }

    @Test
    @DisplayName("PASSO 8.3 — 'rilascia 2 posti' eseguito due volte ne rilascia 2, non 4")
    void rilascioRipetutoNonRimetteDueVolte() {
        Long showId = spettacoloCon(10);
        service.reserveSeats(showId, 2, "saga-rilascio");
        assertThat(postiLiberi(showId)).isEqualTo(8);

        service.releaseSeats(showId, 2, "saga-rilascio");
        assertThat(postiLiberi(showId)).isEqualTo(10);

        // La compensazione ripetuta. Il Math.min di Show.releaseSeats qui
        // basterebbe per caso — il totale e' 10 — ma non basterebbe su uno
        // spettacolo con altre prenotazioni in corso: sono quelle a essere
        // regalate. La riga in show_operations non lascia la cosa al caso.
        service.releaseSeats(showId, 2, "saga-rilascio");
        assertThat(postiLiberi(showId)).isEqualTo(10);
    }

    @Test
    @DisplayName("La stessa saga puo' riservare E rilasciare: il vincolo e' sulla COPPIA")
    void laStessaSagaPuoFareEntrambe() {
        Long showId = spettacoloCon(10);

        service.reserveSeats(showId, 3, "saga-andata-e-ritorno");
        service.releaseSeats(showId, 3, "saga-andata-e-ritorno");

        assertThat(postiLiberi(showId)).isEqualTo(10);
        assertThat(operazioni.findBySagaIdAndOperationType(
                "saga-andata-e-ritorno", TipoOperazione.RESERVE)).isPresent();
        assertThat(operazioni.findBySagaIdAndOperationType(
                "saga-andata-e-ritorno", TipoOperazione.RELEASE)).isPresent();
    }

    @Test
    @DisplayName("Due sagaId diversi sono due acquisti diversi, e scalano entrambi")
    void sagheDiverseScalanoEntrambe() {
        Long showId = spettacoloCon(10);

        service.reserveSeats(showId, 2, "saga-cliente-uno");
        service.reserveSeats(showId, 2, "saga-cliente-due");

        // La protezione non deve diventare un blocco: due clienti diversi
        // comprano davvero due volte.
        assertThat(postiLiberi(showId)).isEqualTo(6);
    }

    @Test
    @DisplayName("Ogni operazione lascia UNA riga, e la riserva ripetuta non ne aggiunge")
    void unaRigaPerOperazione() {
        Long showId = spettacoloCon(10);

        service.reserveSeats(showId, 1, "saga-conteggio");
        service.reserveSeats(showId, 1, "saga-conteggio");
        service.reserveSeats(showId, 1, "saga-conteggio");

        Integer righe = jdbc.queryForObject(
                "SELECT count(*) FROM show_operations WHERE saga_id = 'saga-conteggio'",
                Integer.class);

        assertThat(righe).isEqualTo(1);
        assertThat(postiLiberi(showId)).isEqualTo(9);
    }
}
