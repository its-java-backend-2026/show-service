package it.its.cinema.showsservice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PASSO 4.1 — un DTO diverso per la modifica, e non e' duplicazione inutile.
 *
 * Creare e modificare accettano dati DIVERSI: in creazione si sceglie il film
 * e la capienza della sala, in modifica no. Uno spettacolo con un altro film e'
 * uno spettacolo diverso, e chi ha gia' prenotato non si aspetta che la sala si
 * rimpicciolisca.
 *
 * Con un unico DTO per entrambe le rotte quei due campi ci sarebbero e
 * andrebbero ignorati a mano nella update: si tornerebbe al problema che il
 * passo 4.1 e' venuto a risolvere.
 */
@Schema(description = "I dati modificabili di uno spettacolo")
public record UpdateShowRequest(

        /**
         * @Future anche qui: uno spettacolo non si sposta nel passato.
         *
         * CONSEGUENZA DA CONOSCERE: siccome PUT richiede TUTTI i campi
         * modificabili, questo vincolo rende impossibile modificare uno
         * spettacolo GIA' PASSATO — anche solo per correggerne il prezzo,
         * perche' il suo startTime andrebbe rimandato indietro cosi' com'e'
         * e verrebbe rifiutato.
         *
         * E' una scelta, non un effetto collaterale: il cartellone di ieri e'
         * un fatto storico e non si riscrive. Se un giorno servisse
         * correggere il passato, la strada non e' togliere @Future da qui —
         * e' una rotta dedicata (PATCH sul solo prezzo), che dichiara di
         * essere un'eccezione invece di aprire un varco per tutti.
         */
        @NotNull(message = "L'orario di inizio e' obbligatorio")
        @Future(message = "L'orario di inizio deve essere nel futuro")
        @Schema(description = "Nuova data e ora di inizio, in ISO-8601. Deve essere futura.",
                example = "2027-01-15T22:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime startTime,

        @NotNull(message = "Il prezzo base e' obbligatorio")
        @DecimalMin(value = "0.00", message = "Il prezzo base non puo' essere negativo")
        @Schema(description = "Nuovo prezzo base, in euro",
                example = "12.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal basePrice) {
}
