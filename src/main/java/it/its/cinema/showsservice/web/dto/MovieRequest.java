package it.its.cinema.showsservice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * PASSO 4.1 — i dati di un film in ingresso, per la POST e per la PUT.
 *
 * UN SOLO DTO per due rotte, al contrario di quanto fatto per gli spettacoli.
 * Non e' incoerenza: creare e modificare un film accettano esattamente gli
 * stessi due campi, e due record identici sarebbero due posti da tenere
 * allineati a mano. Sugli spettacoli i campi erano davvero diversi, e li'
 * infatti i DTO sono due.
 *
 * La regola e': si separano quando i dati divergono, non per simmetria.
 * Il giorno in cui la POST accettera' qualcosa che la PUT non accetta, questo
 * record si sdoppia — e sara' un cambiamento di dieci righe.
 *
 * Cosa NON c'e': l'id. Nella POST veniva ignorato, nella PUT veniva confrontato
 * con quello dell'URL per rifiutare le richieste contraddittorie. Ora non puo'
 * proprio arrivare: il film si indica nell'URL, punto. Il controllo di coerenza
 * fra body e path sparisce perche' sparisce la contraddizione.
 */
@Schema(description = "I dati di un film")
public record MovieRequest(

        @NotBlank(message = "Il titolo e' obbligatorio")
        @Size(max = 200, message = "Il titolo non puo' superare i 200 caratteri")
        @Schema(description = "Titolo del film, unico in catalogo",
                example = "Dune - Parte Due", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        /**
         * Integer e non int: con il primitivo un campo mancante arriverebbe
         * come zero e l'errore direbbe "la durata deve essere positiva"
         * invece di "la durata e' obbligatoria". Vedi CreateShowRequest.
         */
        @NotNull(message = "La durata e' obbligatoria")
        @Positive(message = "La durata deve essere positiva")
        @Schema(description = "Durata in minuti",
                example = "166", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer durationMinutes) {
}
