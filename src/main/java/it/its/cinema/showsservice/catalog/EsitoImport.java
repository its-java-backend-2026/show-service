package it.its.cinema.showsservice.catalog;

/**
 * Cosa ha fatto un import: quanti film nuovi, quanti c'erano gia', quanti ne
 * ha offerti la sorgente.
 *
 * Serve perche' l'import remoto e' chiamato da HTTP e una risposta "fatto"
 * non dice niente: il client deve poter distinguere "ne ho presi 4" da
 * "non c'era niente di nuovo", che sono due esiti entrambi corretti.
 */
public record EsitoImport(int importati, int giaPresenti, int totaleNellaSorgente) {
}
