package it.its.cinema.showsservice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PASSO 4.1 — il film come lo vede chi consuma l'API.
 *
 * Oggi ha gli stessi campi dell'entita' Movie, e sembra un doppione. Non lo e':
 * il punto non e' che i campi siano diversi ADESSO, e' che possano diventarlo
 * senza rompere niente. Il giorno in cui la colonna "minutes" viene rinominata,
 * o si aggiunge un campo interno che al pubblico non deve arrivare, cambia il
 * mapper e il contratto HTTP resta quello di prima.
 */
@Schema(description = "Un film in catalogo")
public record MovieResponse(

        @Schema(description = "Identificativo del film", example = "1")
        Long id,

        @Schema(description = "Titolo", example = "Dune - Parte Due")
        String title,

        @Schema(description = "Durata in minuti", example = "166")
        int durationMinutes,

        @Schema(description = "Contatore per il lock ottimistico", example = "0")
        Long version) {
}
