package it.its.cinema.showsservice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PASSO 6.8b — l'esito di un import, come lo vede chi chiama l'API.
 *
 * E' il gemello di EsitoImport, che vive nel package catalog/. Sembrano lo
 * stesso record e per oggi lo sono: la ragione per tenerli separati e' la
 * stessa di MovieResponse contro Movie — cio' che pubblichiamo non deve
 * cambiare solo perche' cambia una struttura interna.
 */
@Schema(description = "Cosa ha prodotto l'import del catalogo remoto")
public record ImportResponse(

        @Schema(description = "Film nuovi effettivamente inseriti", example = "4")
        int importati,

        @Schema(description = "Film del fornitore che erano gia' in catalogo", example = "3")
        int giaPresenti,

        @Schema(description = "Film offerti dal fornitore", example = "7")
        int totaleDalFornitore) {
}
