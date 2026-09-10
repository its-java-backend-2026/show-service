package it.its.cinema.showsservice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PASSO 4.1 — IL DTO IN USCITA.
 *
 * Fino a ieri il controller restituiva l'entita' Show, e il JSON era il
 * riflesso della tabella. Sembra comodo e costa caro:
 *
 *  1. ogni rinomina di un campo JPA e' un cambiamento incompatibile per il
 *     client, anche quando nessuno voleva cambiare l'API;
 *  2. il JSON si portava dietro l'oggetto movie annidato per intero, durata
 *     compresa, che a chi legge uno spettacolo non serve;
 *  3. Jackson e Hibernate finiscono per litigare sul dominio, ed e' il motivo
 *     dei @JsonCreator(mode = DISABLED) che oggi spariscono da Show e Movie.
 *
 * Il DTO taglia il legame: la tabella e il contratto HTTP possono cambiare
 * ognuno per conto suo, e in mezzo c'e' il mapper (passo 4.2).
 *
 * RECORD e non classe Lombok, ed e' la trappola del passo 4.1: un DTO con
 * @Builder non ha costruttore pubblico e Jackson non lo sa costruire ->
 *     InvalidDefinitionException: no Creators, like default constructor, exist
 * Il record il costruttore canonico ce l'ha, ed e' pubblico. (Se il builder
 * serve davvero: @Jacksonized accanto a @Builder.)
 *
 * Il film e' APPIATTITO in due campi invece che annidato: chi mostra un
 * cartellone vuole il titolo, non l'oggetto film. Chi vuole il film intero
 * chiede GET /movies/{id}.
 */
@Schema(description = "Uno spettacolo in programmazione")
public record ShowResponse(

        @Schema(description = "Identificativo dello spettacolo", example = "1")
        Long id,

        @Schema(description = "Identificativo del film proiettato", example = "1")
        Long movieId,

        @Schema(description = "Titolo del film proiettato", example = "Dune - Parte Due")
        String movieTitle,

        @Schema(description = "Data e ora di inizio", example = "2026-10-15T21:00:00")
        LocalDateTime startTime,

        @Schema(description = "Prezzo base del biglietto, in euro", example = "9.50")
        BigDecimal basePrice,

        @Schema(description = "Posti totali della sala", example = "120")
        int totalSeats,

        @Schema(description = "Posti ancora disponibili", example = "118")
        int availableSeats,

        @Schema(description = "Vero dalle 20:00 in poi", example = "true")
        boolean eveningShow,

        /**
         * Il contatore del lock ottimistico (passo 3.7).
         *
         * Esce ANCHE se oggi nessuno lo rimanda indietro: e' il dato che
         * serve al client per fare "modifica solo se nessuno ha toccato
         * niente" il giorno in cui la PUT lo accettera'.
         */
        @Schema(description = "Contatore per il lock ottimistico", example = "0")
        Long version) {
}
