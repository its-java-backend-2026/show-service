package it.its.cinema.showsservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.MovieNotFoundException;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.domain.ShowNotFoundException;
import it.its.cinema.showsservice.repository.MovieRepository;
import it.its.cinema.showsservice.repository.ShowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * PASSO 1.8 — lo strato che conosce le regole del caso d'uso.
 *
 * @RequiredArgsConstructor genera il costruttore con tutti i campi final:
 * e' INIEZIONE DA COSTRUTTORE, ed e' l'unica da usare.
 *   - @Autowired sul campo nasconde le dipendenze e rende la classe non
 *     istanziabile in un test senza Spring
 *   - un campo final non puo' restare null: se manca un bean l'applicazione
 *     non parte, invece di esplodere alla prima richiesta
 *
 * Il service dipende da ShowRepository (l'interfaccia), non da
 * InMemoryShowRepository: e' per questo che domani non cambia niente qui.
 */
/**
 * PASSO 3.6 — LE TRANSAZIONI STANNO QUI.
 *
 * Non nel controller: li' la transazione resterebbe aperta durante la
 * serializzazione HTTP, e ogni lentezza del client diventerebbe una
 * transazione lunga sul database.
 * Non nel repository: un caso d'uso che tocca due tabelle deve riuscire o
 * fallire tutto insieme, e questo lo sa solo il service.
 *
 * DUE MODI DI RENDERLA INUTILE, ENTRAMBI SILENZIOSI:
 *
 *  1. self-invocation. Un metodo @Transactional chiamato da un altro metodo
 *     DELLA STESSA CLASSE non passa dal proxy di Spring e non apre niente.
 *     Per questo riservaPosti() qui sotto NON chiama perId(), ma rilegge dal
 *     repository: la sua @Transactional deve valere per tutto il metodo.
 *
 *  2. @Transactional su un metodo non pubblico: il proxy non lo intercetta.
 *
 * In entrambi i casi non c'e' errore, non c'e' warning: semplicemente la
 * transazione non c'e'.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShowService {

    private final ShowRepository repository;
    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public Page<Show> findAll(Pageable pageable) {
        // readOnly = true non e' cosmetico: Hibernate salta il dirty checking
        // e il database puo' instradare la query su una replica di lettura
        return repository.findAllBy(pageable);
    }

    /**
     * Lo spettacolo richiesto, oppure ShowNotFoundException
     * Il service non restituisce Optional al controller: e' lui che sa che
     * "non c'e'" e' una situazione anomala. Il controller decide solo come
     * raccontarla in HTTP.
     */
    @Transactional(readOnly = true)
    public Show findById(Long id) {
        return repository.findWithMovieById(id)
                .orElseThrow(() -> new ShowNotFoundException(id));
    }

    /**
     * findWithMovieById e non findById: il JSON in uscita contiene il film, e
     * viene costruito FUORI dalla transazione (open-in-view e' false).
     * Con il film LAZY e non caricato si prende LazyInitializationException
     * a runtime — e nessun @DataJpaTest se ne accorge, perche' li' la
     * transazione resta aperta per tutto il test.
     */
    @Transactional(readOnly = true)
    public Show perId(Long id) {
        return repository.findWithMovieById(id)
                .orElseThrow(() -> new ShowNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Show> perFilmEIntervallo(Long movieId, LocalDateTime da, LocalDateTime a) {
        return repository.findByFilmAndInterval(movieId, da, a);
    }

    /**
     * Riserva dei posti. Il service NON fa il calcolo: chiede allo Show di
     * farlo (la regola sta nel dominio) e poi si occupa di rendere persistente
     * il risultato, che e' l'unica cosa che il dominio non sa fare.
     */
    @Transactional
    public Show getAvailableSeats(Long id, int quantita) {
        Show show = findById(id);
        show.reserveSeats(quantita);
        log.info("riservati {} posti sullo spettacolo {}, ne restano {}",
                quantita, id, show.getAvailableSeats());
        return repository.save(show);
    }

     /** 404 se non esiste:  */
     @Transactional
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new ShowNotFoundException(id);
        }
        repository.deleteById(id);
        log.info("eliminato lo spettacolo {}", id);
    }

    /**
     * Aggiunge al catalogo uno spettacolo GIA' COSTRUITO.
     *
     * Lo usano i test, che hanno in mano un oggetto Movie vero e non un id.
     * La rotta POST /shows passa invece dall'overload qui sotto, che parte
     * dai dati della richiesta e risolve il film.
     */
    @Transactional
    public Show addShow(Show show) {
        log.info("aggiunto lo spettacolo {} ({} posti)",
                show.getId(), show.getTotalSeats());
        return repository.save(show);
    }

    /**
     * Crea uno spettacolo A PARTIRE DAI DATI DELLA RICHIESTA.
     *
     * E' questo che usa POST /shows, e non addShow, per due ragioni.
     *
     * 1. IL FILM VA RISOLTO. Il client manda un movieId; il database vuole una
     *    riga che esista davvero. Rileggendolo qui, un film inesistente diventa
     *    una MovieNotFoundException -> 404 "il film non c'e'", invece di una
     *    violazione di foreign key -> 500 "mi sono rotto". E lo Show salvato si
     *    porta dietro il film vero, non lo stub {id: 1, title: null} arrivato
     *    dalla richiesta.
     *
     * 2. id E availableSeats NON SONO DATI DEL CLIENT, SONO CONSEGUENZE. Qui
     *    nemmeno arrivano: la firma prende solo i quattro campi che il client
     *    ha il diritto di decidere. L'id lo assegna il database; i posti
     *    disponibili li calcola il costruttore di Show, che parte pieno.
     *    Una firma che non accetta un dato e' piu' solida di un commento che
     *    ricorda di ignorarlo.
     */
    @Transactional
    public Show create(Long movieId, LocalDateTime startTime, BigDecimal basePrice, int totalSeats) {
        if (movieId == null) {
            throw new IllegalArgumentException("movie.id e' obbligatorio");
        }
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));

        // le altre validazioni le fa il costruttore: la regola sta nel dominio
        Show creato = repository.save(new Show(null, movie, startTime, basePrice, totalSeats));
        log.info("creato lo spettacolo {} per il film {} ({} posti)",
                creato.getId(), movieId, totalSeats);
        return creato;
    }

    /**
     * Aggiorna orario e prezzo.
     *
     * Il film non si cambia da qui: uno spettacolo che cambia film e' uno
     * spettacolo diverso, non lo stesso modificato. E i posti nemmeno: chi ha
     * gia' prenotato non si aspetta che la sala si rimpicciolisca.
     */
    @Transactional
    public Show update(Long id, LocalDateTime startTime, BigDecimal basePrice) {
        Show show = findById(id);
        if (startTime == null) {
            throw new IllegalArgumentException("startTime e' obbligatorio");
        }
        if (basePrice == null || basePrice.signum() < 0) {
            throw new IllegalArgumentException("basePrice non puo' essere negativo");
        }
        show.setStartTime(startTime);
        show.setBasePrice(basePrice);
        log.info("aggiornato lo spettacolo {}", id);
        return repository.save(show);
    }

    /**
     * Riserva dei posti. Il service NON fa il calcolo: chiede allo Show di
     * farlo (la regola sta nel dominio) e poi si occupa di rendere persistente
     * il risultato, che e' l'unica cosa che il dominio non sa fare.
     *
     * Dal G8 questo metodo diventa il primo passo della saga di prenotazione.
     */
    @Transactional
    public Show reserveSeats(Long id, int quantita) {
        Show show = findById(id);
        show.reserveSeats(quantita);
        log.info("riservati {} posti sullo spettacolo {}, ne restano {}",
                quantita, id, show.getAvailableSeats());
        return repository.save(show);
    }

    /**
     * Restituisce dei posti al pubblico.
     *
     * Come riservaPosti, il service non fa il calcolo: lo chiede allo Show,
     * perche' il tetto dei posti totali e' una regola di dominio.
     * Dal G8 diventa la COMPENSAZIONE del primo passo della saga: quando il
     * pagamento viene rifiutato, i posti devono tornare disponibili.
     */
    @Transactional
    public Show releaseSeats(Long id, int quantita) {
        Show show = findById(id);
        show.releaseSeats(quantita);
        log.info("rilasciati {} posti sullo spettacolo {}, ora ne ha {}",
                quantita, id, show.getAvailableSeats());
        return repository.save(show);
    }
}
