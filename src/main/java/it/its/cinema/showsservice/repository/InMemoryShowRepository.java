package it.its.cinema.showsservice.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.Show;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * L'unica implementazione di ShowRepository che esiste oggi.
 *
 * @Repository e non @Component solo per leggibilita': fanno la stessa cosa
 * (registrano un bean), ma il nome dice a che strato appartiene la classe.
 *
 * ConcurrentHashMap e AtomicLong e non HashMap e long: un server web serve
 * piu' richieste contemporaneamente, su thread diversi. Con una HashMap due
 * POST simultanee possono corrompere la mappa.
 *
 * TUTTA QUESTA CLASSE SPARISCE AL G2, sostituita da PostgreSQL. E' un
 * ponteggio, ed e' giusto che si veda che lo e'.
 */
@Repository
@Slf4j
public class InMemoryShowRepository implements ShowRepository {

    private final Map<Long, Show> shows = new ConcurrentHashMap<>();
    private final AtomicLong sequenza = new AtomicLong(0);

    @Override
    public Show save(Show show) {
        if (show.getId() == null) {
            // l'id lo assegna chi conserva i dati, non chi li crea:
            // al G2 lo fara' la sequenza di PostgreSQL con @GeneratedValue
            show.setId(sequenza.incrementAndGet());
        }
        shows.put(show.getId(), show);
        return show;
    }

    @Override
    public Optional<Show> findById(Long id) {
        return Optional.ofNullable(shows.get(id));
    }

    @Override
    public List<Show> findAll() {
        // ordinati per orario: con una mappa l'ordine di inserimento non e' garantito,
        // e una lista che cambia ordine a ogni chiamata e' difficile da provare
        return shows.values().stream()
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return shows.containsKey(id);
    }

    @Override
    public void deleteById(Long id) {
        shows.remove(id);
    }

    /**
     * Dati di esempio, per avere qualcosa da leggere al passo 1.9.
     * Dal G2 il catalogo arriva dalle migrazioni Flyway, e questo metodo sparisce.
     */
    @PostConstruct
    void datiDiEsempio() {
        Movie dune = new Movie(1L, "Dune - Parte Due", 166);
        Movie oppenheimer = new Movie(2L, "Oppenheimer", 180);

        LocalDateTime oggi = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        save(new Show(null, dune, oggi.withHour(17), new BigDecimal("8.50"), 120));
        save(new Show(null, dune, oggi.withHour(21), new BigDecimal("10.00"), 120));
        save(new Show(null, oppenheimer, oggi.withHour(20).plusDays(1), new BigDecimal("9.00"), 80));

        log.info("caricati {} spettacoli di esempio in memoria", shows.size());
    }
}

