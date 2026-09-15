package it.its.cinema.showsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.NotEnoughSeatsException;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.repository.MovieRepository;
import it.its.cinema.showsservice.repository.ShowRepository;
import it.its.cinema.showsservice.service.ShowService;

/**
 * CONSEGNA G3 — la prova che due prenotazioni concorrenti non vendono
 * lo stesso posto due volte.
 *
 * Si chiama *IT e non *Test: lo esegue failsafe su "mvn verify", non
 * surefire su "mvn test". Cosi' "mvn test" resta veloce e non pretende
 * Docker acceso.
 *
 * PostgreSQL VERO con Testcontainers: H2 non e' PostgreSQL, e le nostre
 * migrazioni usano BIGSERIAL e generate_series.
 *
 * @ServiceConnection sostituisce le vecchie @DynamicPropertySource: prende
 * url, utente e password dal container e li mette nel contesto da solo.
 */
@SpringBootTest
@Testcontainers
class ShowRepositoryIT {

    private static final int POSTI = 4;
    private static final int CLIENTI = 8;

    /**
     * Quanti giri fa il retry prima di arrendersi. Generoso di proposito: il
     * ciclo esce da solo appena vende o appena scopre che i posti sono finiti,
     * quindi un numero alto non rallenta niente. Se li esaurisce davvero, il
     * test deve fallire dicendolo, non passare in silenzio.
     */
    private static final int TENTATIVI = 20;

    @Container
    @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private ShowService service;

    @Autowired
    private ShowRepository repository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private TransactionTemplate tx;

    private Long spettacoloCon(int posti) {
        return tx.execute(stato -> {
            Movie film = movieRepository.save(new Movie("Film di prova 4" + System.nanoTime(), 100));
            // il primo argomento e' l'id: null perche' lo assegna il database
            Show show = new Show(null, film, LocalDateTime.of(2026, 11, 1, 21, 0),
                    new BigDecimal("10.00"), posti);
            return repository.save(show).getId();
        });
    }

    /**
     * IL PEZZO CHE MANCA AL LOCK OTTIMISTICO: il retry.
     *
     * OptimisticLockingFailureException non significa "non ci sono posti",
     * significa "qualcun altro ha scritto prima di te, rileggi e riprova".
     * Senza retry, con otto thread davvero simultanei si vende UN posto su
     * quattro: tutti leggono version = 0, uno vince, sette si arrendono, e
     * tre poltrone restano invendute con sette clienti in fila che le
     * volevano. Sicuro, ma sciocco.
     *
     * "Ottimistico" vuol dire: scommetto che non ci saranno conflitti, e se
     * perdo la scommessa RICOMINCIO. La seconda meta' della frase e' quella
     * che si dimentica.
     *
     * Qui il retry sta nel test per non sporcare il dominio. In produzione
     * sta al confine dell'applicazione — Spring Retry con @Retryable, o un
     * ciclo nel service — e mai nel dominio, che non sa niente di transazioni.
     *
     * =======================================================================
     * PASSO 8.3 — E DAL G8 IL sagaId QUI SOPRA NON E' PIU' UNA DECORAZIONE.
     *
     * Fino al G7 serviva solo a rendere leggibili i log, e infatti questo
     * test lo componeva col NUMERO DEL TENTATIVO: otto clienti diversi al
     * primo giro mandavano tutti "test-concorrenza-1". Andava bene finche'
     * shows-service lo scriveva e lo dimenticava.
     *
     * Da oggi quella stringa e' una CHIAVE DI IDEMPOTENZA: otto clienti con
     * lo stesso sagaId sono, per shows-service, lo stesso acquisto ripetuto
     * otto volte — e verrebbe eseguito una volta sola, vendendo un posto
     * invece di quattro. Il test fallirebbe, e avrebbe ragione.
     *
     * Ogni cliente ha quindi il SUO sagaId, che e' cio' che succede davvero:
     * booking-service ne genera uno nuovo per ogni tentativo di acquisto
     * (passo 6.4). Il numero del tentativo invece NON entra nella chiave, ed
     * e' voluto: un retry dopo un conflitto e' lo STESSO acquisto, e deve
     * portare la stessa identita'.
     * =======================================================================
     *
     * @return true se il posto e' stato venduto, false se erano finiti
     */
    private boolean prenotaUnPostoConRetry(Long showId, String sagaId, AtomicInteger conflitti) {
        for (int tentativo = 1; tentativo <= TENTATIVI; tentativo++) {
            try {
                service.reserveSeats(showId, 1, sagaId);
                return true;
            } catch (NotEnoughSeatsException postiFiniti) {
                // esito legittimo, non un conflitto: non si riprova
                return false;
            } catch (OptimisticLockingFailureException conflitto) {
                conflitti.incrementAndGet();
                attendiUnPo();
            }
        }
        throw new IllegalStateException("esauriti i " + TENTATIVI
                + " tentativi senza ne' vendere il posto ne' trovare i posti finiti");
    }

    /**
     * Backoff con jitter. Il jitter non e' un vezzo: ripartendo tutti nello
     * stesso istante i thread si scontrerebbero di nuovo allo stesso modo e
     * il retry non servirebbe a niente.
     */
    private void attendiUnPo() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 25));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrotto durante il backoff", e);
        }
    }

    @Test
    @DisplayName("con il retry si vendono tutti i posti, e non uno di piu'")
    void loStessoPostoNonSiVendeDueVolte() throws Exception {
        Long showId = spettacoloCon(POSTI);

        ExecutorService pool = Executors.newFixedThreadPool(CLIENTI);
        CountDownLatch via = new CountDownLatch(1);
        CountDownLatch finiti = new CountDownLatch(CLIENTI);
        AtomicInteger riusciti = new AtomicInteger();
        AtomicInteger respinti = new AtomicInteger();
        AtomicInteger conflitti = new AtomicInteger();
        AtomicReference<Throwable> inatteso = new AtomicReference<>();

        for (int i = 0; i < CLIENTI; i++) {
            // Un sagaId per cliente: sono otto acquisti diversi, non uno
            // ripetuto otto volte. Vedi il commento su prenotaUnPostoConRetry.
            String sagaId = "test-concorrenza-cliente-" + i + "-" + System.nanoTime();
            pool.submit(() -> {
                try {
                    via.await();                       // partono tutti insieme
                    if (prenotaUnPostoConRetry(showId, sagaId, conflitti)) {
                        riusciti.incrementAndGet();
                    } else {
                        respinti.incrementAndGet();
                    }
                } catch (Throwable t) {
                    // NIENTE viene ingoiato: un errore inatteso deve far fallire il
                    // test. Un catch vuoto lo farebbe passare per il motivo
                    // sbagliato, ed e' il difetto piu' comune dei test concorrenti.
                    inatteso.compareAndSet(null, t);
                } finally {
                    finiti.countDown();
                }
            });
        }
        via.countDown();
        assertTrue(finiti.await(60, TimeUnit.SECONDS), "i thread non hanno finito in tempo");
        pool.shutdown();

        Throwable errore = inatteso.get();
        if (errore != null) {
            throw new AssertionError("un cliente ha fallito per un motivo inatteso", errore);
        }

        Show finale = repository.findById(showId).orElseThrow();

        System.out.printf("%d clienti su %d posti -> %d venduti, %d respinti, "
                        + "%d conflitti superati col retry, %d posti liberi%n",
                CLIENTI, POSTI, riusciti.get(), respinti.get(),
                conflitti.get(), finale.getAvailableSeats());

        // 1. SICUREZZA — i posti spariti sono ESATTAMENTE quelli venduti.
        // Senza @Version questa salta: tutti leggono 4, tutti scrivono 3, otto
        // clienti ricevono "ok" e il database ne ha scalato uno. E il
        // CHECK (available_seats >= 0) della migrazione non salva: 3 e' legale.
        assertEquals(POSTI - riusciti.get(), finale.getAvailableSeats(),
                "posti venduti e posti scalati non coincidono: qualcuno ha sovrascritto");

        // 2. NESSUNA VENDITA PERSA — col retry i quattro posti si vendono tutti.
        // Senza retry qui ne passerebbe uno solo: e' la differenza fra
        // "sicuro" e "sicuro e utile".
        assertEquals(POSTI, riusciti.get(),
                "col retry i posti devono vendersi tutti, venduti invece " + riusciti.get());
        assertEquals(0, finale.getAvailableSeats(), "restano posti liberi con clienti respinti");

        // 3. NESSUN CLIENTE NEL LIMBO — ogni thread e' finito in una delle due
        // categorie previste, quindi i conti sopra parlano di tutti e otto.
        assertEquals(CLIENTI, riusciti.get() + respinti.get(),
                "qualche cliente non e' finito ne' fra i venduti ne' fra i respinti");
    }
}
