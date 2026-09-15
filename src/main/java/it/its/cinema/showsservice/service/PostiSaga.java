package it.its.cinema.showsservice.service;

import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.domain.ShowNotFoundException;
import it.its.cinema.showsservice.domain.ShowOperation;
import it.its.cinema.showsservice.domain.TipoOperazione;
import it.its.cinema.showsservice.repository.ShowOperationRepository;
import it.its.cinema.showsservice.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PASSI 8.3 e 8.6 — LE DUE SCRITTURE CHE DEVONO ANDARE INSIEME.
 *
 * ===========================================================================
 * PERCHE' UNA CLASSE A PARTE, E NON DUE METODI PRIVATI DI ShowService.
 *
 * E' la regola del passo 8.6, e questo servizio la conosceva gia': il
 * commento in cima a ShowService la spiega dal G3. @Transactional funziona
 * SOLO se la chiamata passa dal proxy di Spring, e un metodo privato — o un
 * metodo pubblico chiamato da un altro metodo della stessa classe — non apre
 * nessuna transazione. In silenzio: nessun errore, nessun warning.
 *
 * Qui serve davvero, perche' le scritture sono DUE e devono essere
 * atomiche:
 *
 *   - la disponibilita' dello spettacolo, che cambia
 *   - la riga in show_operations, che dice che questa saga e' passata
 *
 * Se passasse solo la prima, la stessa richiesta ritentata scalerebbe i
 * posti una seconda volta, perche' non troverebbe nessuna operazione
 * registrata: sarebbe il G7 daccapo, con in piu' una tabella che da' una
 * falsa sicurezza. Se passasse solo la seconda, i posti non tornerebbero
 * mai indietro.
 *
 * E c'e' un secondo motivo, piu' sottile: ShowService deve poter trattare la
 * violazione del vincolo UNIQUE come una RISPOSTA ("l'ha gia' fatto
 * qualcun altro") e non come un errore. Dentro una transazione non si puo':
 * la violazione la marca rollback-only, e ogni operazione successiva —
 * compresa la rilettura — muore al commit con UnexpectedRollbackException.
 * Tenendo la transazione QUI dentro, quella che fallisce muore da sola e
 * chi chiama puo' rileggere il mondo com'e' rimasto.
 * ===========================================================================
 */
@Component
@RequiredArgsConstructor
public class PostiSaga {

    private final ShowRepository repository;
    private final ShowOperationRepository operazioni;

    /**
     * La riserva: posti scalati e operazione registrata, o niente di tutto
     * questo.
     *
     * Nota che il calcolo NON e' qui: lo fa Show.reserveSeats, perche' la
     * regola ("non si scende sotto zero") sta nel dominio. Questa classe si
     * occupa solo di rendere persistente il risultato insieme alla sua
     * prova.
     */
    @Transactional
    public Show riserva(Long showId, int quantita, String sagaId) {
        Show show = repository.findWithMovieById(showId)
                .orElseThrow(() -> new ShowNotFoundException(showId));

        show.reserveSeats(quantita);
        operazioni.save(new ShowOperation(sagaId, TipoOperazione.RESERVE, showId, quantita));

        return repository.save(show);
    }

    /** Il rilascio: la compensazione, con la stessa atomicita'. */
    @Transactional
    public Show rilascia(Long showId, int quantita, String sagaId) {
        Show show = repository.findWithMovieById(showId)
                .orElseThrow(() -> new ShowNotFoundException(showId));

        show.releaseSeats(quantita);
        operazioni.save(new ShowOperation(sagaId, TipoOperazione.RELEASE, showId, quantita));

        return repository.save(show);
    }
}
