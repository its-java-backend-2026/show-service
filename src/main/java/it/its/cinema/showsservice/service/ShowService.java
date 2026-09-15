package it.its.cinema.showsservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.MovieNotFoundException;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.domain.ShowNotFoundException;
import it.its.cinema.showsservice.domain.TipoOperazione;
import it.its.cinema.showsservice.repository.MovieRepository;
import it.its.cinema.showsservice.repository.ShowOperationRepository;
import it.its.cinema.showsservice.repository.ShowRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** PASSO 8.3 — "questa saga l'ho gia' vista?". */
    private final ShowOperationRepository operazioni;

    /** PASSO 8.6 — le due scritture che devono andare insieme. */
    private final PostiSaga postiSaga;

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
     * PASSO 6.4 — E' UN PASSO DI UNA SAGA, E LO DICE LA FIRMA.
     *
     * Il sagaId non cambia una virgola del calcolo: serve a rendere leggibile
     * cio' che succede su piu' processi diversi. Senza, nei log di
     * shows-service si legge "riservati 2 posti sullo spettacolo 1" e non c'e'
     * modo di capire a quale acquisto appartenga fra i cento in corso.
     *
     * =======================================================================
     * PASSO 8.3 — E DAL G8 IL sagaId E' ANCHE LA CHIAVE DELL'IDEMPOTENZA.
     *
     * Era la promessa lasciata scritta al G6 e al G7, ed e' il debito che
     * questo metodo ripaga oggi. Fino a ieri, ritentare una riserva dopo un
     * timeout scalava i posti una seconda volta: per questo, in
     * booking-service, era l'unica chiamata senza @Retry.
     *
     * Ora la seconda chiamata con lo stesso sagaId NON FA NIENTE e risponde
     * come la prima. Il che permette a chi ci chiama di ritentare in
     * sicurezza — ed e' esattamente cio' che serve, perche' "timeout" non
     * vuol dire "non e' arrivata": vuol dire "non so se e' arrivata".
     *
     * NIENTE @Transactional QUI SOPRA, E NON E' UNA DIMENTICANZA: la
     * transazione sta in PostiSaga, perche' la violazione del vincolo UNIQUE
     * dev'essere trattata come una risposta e non come un errore. Il perche'
     * per esteso e' nel commento di quella classe.
     * =======================================================================
     */
    public Show reserveSeats(Long id, int quantita, String sagaId) {

        // --- l'ho gia' fatta? il caso normale: un retry dopo un timeout ---
        if (operazioni.existsBySagaIdAndOperationType(sagaId, TipoOperazione.RESERVE)) {
            log.info("[saga {}] riserva gia' eseguita sullo spettacolo {}: non scalo niente",
                    sagaId, id);
            return perId(id);
        }

        try {
            Show show = postiSaga.riserva(id, quantita, sagaId);
            log.info("[saga {}] riservati {} posti sullo spettacolo {}, ne restano {}",
                    sagaId, quantita, id, show.getAvailableSeats());
            return show;

        } catch (DataIntegrityViolationException e) {
            // La corsa persa: due chiamate con lo stesso sagaId sono arrivate
            // insieme e hanno superato entrambe il controllo qui sopra. Il
            // vincolo ne ha fatta passare una, e la NOSTRA transazione e'
            // stata annullata tutta — posti compresi. Non c'e' nessun doppio
            // scalo da disfare: si rilegge e si risponde come l'altra.
            log.warn("[saga {}] corsa persa sulla riserva dello spettacolo {}: "
                    + "ha vinto un'altra chiamata, rileggo", sagaId, id);
            return perId(id);
        }
    }

    /**
     * Restituisce dei posti al pubblico.
     *
     * Come reserveSeats, il service non fa il calcolo: lo chiede allo Show,
     * perche' il tetto dei posti totali e' una regola di dominio.
     * Dal G8 e' la COMPENSAZIONE del primo passo della saga: quando il
     * pagamento viene rifiutato, i posti devono tornare disponibili.
     *
     * PASSO 6.4 — il sagaId e' quello del reserve che si sta compensando:
     * e' la stringa che, cercata nei log, mostra l'andata e il ritorno dello
     * stesso acquisto.
     *
     * =======================================================================
     * PASSO 8.3 — "RILASCIA 2 POSTI" ESEGUITO DUE VOLTE NE RILASCIA 2, NON 4.
     *
     * E' la frase del passo 8.3, e qui va letta due volte perche' e' il caso
     * in cui il danno si nota MENO: dei posti in regalo non fanno arrabbiare
     * nessuno subito, e la sera della proiezione si scopre che due file sono
     * state vendute a quattro persone.
     *
     * Il Math.min dentro Show.releaseSeats non bastava: impedisce di superare
     * il totale, ma non impedisce di rimettere due volte dei posti che erano
     * stati tolti una volta sola. A distinguere i due casi e' solo la riga in
     * show_operations.
     *
     * E c'e' un secondo caso, che il vincolo sulla COPPIA rende possibile:
     * una compensazione che arriva senza che la riserva sia mai avvenuta. Qui
     * non e' un problema — il rilascio viene eseguito e i posti tornano al
     * massimo al totale — ma vale la pena notare che il tipo nella chiave e'
     * cio' che permette alla stessa saga di riservare E rilasciare.
     * =======================================================================
     */
    public Show releaseSeats(Long id, int quantita, String sagaId) {

        if (operazioni.existsBySagaIdAndOperationType(sagaId, TipoOperazione.RELEASE)) {
            log.info("[saga {}] rilascio gia' eseguito sullo spettacolo {}: non rimetto niente",
                    sagaId, id);
            return perId(id);
        }

        try {
            Show show = postiSaga.rilascia(id, quantita, sagaId);
            log.info("[saga {}] rilasciati {} posti sullo spettacolo {}, ora ne ha {}",
                    sagaId, quantita, id, show.getAvailableSeats());
            return show;

        } catch (DataIntegrityViolationException e) {
            log.warn("[saga {}] corsa persa sul rilascio dello spettacolo {}: "
                    + "ha vinto un'altra chiamata, rileggo", sagaId, id);
            return perId(id);
        }
    }
}
