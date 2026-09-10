package it.its.cinema.showsservice.web.mapper;

import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.web.dto.ShowResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PASSO 4.2 — IL MAPPER, SCRITTO A MANO.
 *
 * Niente MapStruct finche' i DTO non sono decine: un generatore di codice si
 * porta dietro un annotation processor, una fase di build in piu' e un livello
 * di magia da spiegare, per risparmiare venti righe che si leggono in dieci
 * secondi. Quando i DTO saranno cinquanta il conto cambia.
 *
 * Sta nel package web e non nel service: il DTO e' un fatto del livello HTTP, e
 * il service non deve sapere che esiste. Se domani lo stesso service servisse
 * anche una coda di messaggi, il suo codice non cambierebbe di una riga.
 *
 * ATTENZIONE, ed e' il punto piu' interessante di oggi:
 * toResponse legge show.getMovie().getTitle(), e lo fa FUORI dalla transazione
 * (open-in-view e' false dal G2, e il mapper gira dopo che il service ha
 * chiuso). Con il film LAZY dal passo 3.1, quella riga funziona solo perche' il
 * repository lo carica con @EntityGraph nella stessa query (passo 3.3).
 * Togliere l'@EntityGraph da ShowRepository non rompe la compilazione: rompe
 * questa riga, a runtime, con LazyInitializationException. E' il passo 3.3 che
 * tiene in piedi il passo 4.2.
 */
@Component
public class ShowMapper {

    public ShowResponse toResponse(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getMovie().getId(),
                show.getMovie().getTitle(),
                show.getStartTime(),
                show.getBasePrice(),
                show.getTotalSeats(),
                show.getAvailableSeats(),
                show.isEveningShow(),
                show.getVersion());
    }

    public List<ShowResponse> toResponse(List<Show> shows) {
        return shows.stream().map(this::toResponse).toList();
    }

    /**
     * Page.map conserva i metadati della pagina (totalElements, totalPages,
     * number, size) e cambia solo il contenuto: ricostruire una PageImpl a mano
     * significa quasi sempre perdere il totale, che e' il dato per cui il
     * client ha chiesto una pagina invece di una lista.
     */
    public Page<ShowResponse> toResponse(Page<Show> shows) {
        return shows.map(this::toResponse);
    }
}
