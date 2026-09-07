package it.its.cinema.showsservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.domain.ShowNotFoundException;
import it.its.cinema.showsservice.repository.ShowRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Service
@RequiredArgsConstructor
@Slf4j
public class ShowService {

    private final ShowRepository repository;

    public List<Show> findAll() {
        return repository.findAll();
    }

    /**
     * Lo spettacolo richiesto, oppure ShowNotFoundException
     * Il service non restituisce Optional al controller: e' lui che sa che
     * "non c'e'" e' una situazione anomala. Il controller decide solo come
     * raccontarla in HTTP.
     */
    public Show findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ShowNotFoundException(id));
    }

    /**
     * Riserva dei posti. Il service NON fa il calcolo: chiede allo Show di
     * farlo (la regola sta nel dominio) e poi si occupa di rendere persistente
     * il risultato, che e' l'unica cosa che il dominio non sa fare.
     */
    public Show getAvailableSeats(Long id, int quantita) {
        Show show = findById(id);
        show.reserveSeats(quantita);
        log.info("riservati {} posti sullo spettacolo {}, ne restano {}",
                quantita, id, show.getAvailableSeats());
        return repository.save(show);
    }

     /** 404 se non esiste:  */
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
    public Show addShow(Show show) {
        log.info("aggiunto lo spettacolo {} ({} posti)",
                show.getId(), show.getTotalSeats());
        return repository.save(show);
    }

    /**
     * Aggiorna orario e prezzo.
     *
     * Il film non si cambia da qui: uno spettacolo che cambia film e' uno
     * spettacolo diverso, non lo stesso modificato. E i posti nemmeno: chi ha
     * gia' prenotato non si aspetta che la sala si rimpicciolisca.
     */
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
    public Show releaseSeats(Long id, int quantita) {
        Show show = findById(id);
        show.releaseSeats(quantita);
        log.info("rilasciati {} posti sullo spettacolo {}, ora ne ha {}",
                quantita, id, show.getAvailableSeats());
        return repository.save(show);
    }
}
