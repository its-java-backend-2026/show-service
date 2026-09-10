package it.its.cinema.showsservice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PASSO 4.1 — IL DTO IN INGRESSO, che e' la meta' importante.
 *
 * Guardare cosa NON c'e': id, version, availableSeats. Non sono dati del
 * client, sono conseguenze — l'id lo assegna il database, version lo gestisce
 * Hibernate, i posti disponibili li calcola il dominio dai posti totali.
 *
 * Fino a ieri quei campi arrivavano lo stesso e il controller li buttava via a
 * mano, ricordandosene. Una firma che non accetta un dato e' piu' solida di un
 * commento che ricorda di ignorarlo: qui la regola e' nel tipo, e chi scrive il
 * prossimo controller non puo' dimenticarsene.
 *
 * PASSO 4.5 — le annotazioni di validazione stanno QUI e non nel controller.
 * Ma da sole non fanno niente: senza @Valid sul parametro del controller non
 * viene validato NIENTE, e non c'e' nessun errore ad avvisare.
 */
@Schema(description = "I dati per creare uno spettacolo")
public record CreateShowRequest(

        @NotNull(message = "Il film e' obbligatorio")
        @Schema(description = "Identificativo di un film gia' in catalogo",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long movieId,

        /**
         * @Future — non si programma uno spettacolo nel passato.
         *
         * L'ordine delle due annotazioni non conta, ma il loro RUOLO si':
         * @NotNull risponde a "il campo c'e'?", @Future a "il valore ha
         * senso?". Su un campo assente @Future tace (per contratto, un null
         * e' valido per ogni vincolo tranne @NotNull), quindi senza @NotNull
         * accanto il campo mancante passerebbe.
         *
         * "Futuro" e' rispetto all'orologio della JVM al momento della
         * richiesta. Uno spettacolo fra dieci secondi passa: il vincolo dice
         * "non nel passato", non "con un preavviso ragionevole". Se servisse
         * anche quello sarebbe una regola di dominio, non un'annotazione.
         */
        @NotNull(message = "L'orario di inizio e' obbligatorio")
        @Future(message = "L'orario di inizio deve essere nel futuro")
        @Schema(description = "Data e ora di inizio, in ISO-8601. Deve essere futura.",
                example = "2027-01-15T21:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime startTime,

        @NotNull(message = "Il prezzo base e' obbligatorio")
        @DecimalMin(value = "0.00", message = "Il prezzo base non puo' essere negativo")
        @Schema(description = "Prezzo base del biglietto, in euro",
                example = "9.50", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal basePrice,

        /**
         * Integer e non int, e non e' pignoleria.
         *
         * Con un int primitivo, un JSON che NON manda totalSeats arriva qui
         * con lo zero di default, e il client si sente rispondere "i posti
         * devono essere positivi" quando il suo errore vero era "hai
         * dimenticato un campo". Con Integer il campo mancante resta null,
         * @NotNull lo riconosce e il messaggio dice la verita'.
         */
        @NotNull(message = "I posti totali sono obbligatori")
        @Positive(message = "I posti totali devono essere positivi")
        @Schema(description = "Posti totali della sala",
                example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer totalSeats) {
}
