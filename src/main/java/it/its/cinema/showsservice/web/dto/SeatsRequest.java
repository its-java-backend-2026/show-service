package it.its.cinema.showsservice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * PASSO 6.4 — IL CORPO DI /reserve E /release.
 *
 * Fino a ieri la quantita' arrivava come query param:
 *     POST /shows/1/reserve?quantity=2
 * Da oggi e' un corpo JSON, e non e' un capriccio estetico: insieme alla
 * quantita' deve viaggiare il SAGA ID, e un identificativo di correlazione
 * appiccicato alla query string e' la strada piu' breve per vederlo finire
 * nei log di accesso di ogni proxy che la richiesta attraversa.
 *
 * LO STESSO RECORD PER DUE ROTTE.
 * reserve e release hanno bisogno esattamente degli stessi due campi, e
 * release e' la COMPENSAZIONE di reserve: due record identici si
 * smetterebbero di aggiornare insieme al primo campo aggiunto.
 *
 * ---------------------------------------------------------------------------
 * COS'E' IL sagaId, OGGI CHE LA SAGA ANCORA NON C'E'
 *
 * E' l'identificativo dell'INTERA operazione di acquisto, generato una volta
 * sola da booking-service e ripetuto identico a ogni passo verso ogni
 * servizio. Oggi (G6) serve solo a leggere i log: tre processi diversi,
 * tre file di log, e una stringa comune per ricucire la storia di UN acquisto.
 *
 * Dal G8 diventa qualcosa di piu': la chiave con cui riconoscere che un
 * "release" e' la compensazione di QUEL "reserve" e non di un altro, e la
 * chiave dell'idempotenza — la stessa saga che ritenta non deve scalare i
 * posti due volte.
 *
 * Per questo si chiede GIA' OGGI, anche se oggi lo scriviamo solo nel log:
 * aggiungerlo al contratto dopo significherebbe cambiarlo mentre due
 * servizi lo stanno gia' usando.
 * ---------------------------------------------------------------------------
 */
@Schema(description = "Quanti posti muovere, e per conto di quale saga")
public record SeatsRequest(

        /**
         * @NotBlank e non @NotNull: una stringa di soli spazi passerebbe
         * @NotNull e sarebbe inutile come identificativo quanto un null.
         */
        @NotBlank(message = "Il sagaId e' obbligatorio")
        @Schema(description = "Identificativo dell'operazione di acquisto, "
                + "generato da chi la coordina e uguale per tutti i suoi passi",
                example = "3f2a1b9c-6d4e-4a7b-9c2f-1e8d0a5b7c31",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String sagaId,

        /**
         * Integer e non int, per la stessa ragione di CreateShowRequest
         * (passo 4.5): con un int, un corpo JSON che dimentica il campo
         * arriverebbe qui con lo zero di default, e il client leggerebbe
         * "la quantita' deve essere positiva" quando il suo errore vero era
         * "hai dimenticato un campo".
         */
        @NotNull(message = "La quantita' e' obbligatoria")
        @Positive(message = "La quantita' deve essere positiva")
        @Schema(description = "Quanti posti", example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity) {
}
