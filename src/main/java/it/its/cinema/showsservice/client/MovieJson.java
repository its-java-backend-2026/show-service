package it.its.cinema.showsservice.client;

/**
 * PASSO 6.8 — DTO DI CONFINE: LA COPIA LOCALE DI UN CONTRATTO REMOTO.
 *
 * E' la forma di una riga del catalogo del fornitore, e nient'altro. Non e'
 * l'entita' Movie (che ha id, version e annotazioni JPA) e non e'
 * MovieResponse (che e' il NOSTRO contratto verso i nostri client): e' cio'
 * che ci manda qualcun altro.
 *
 * E' una COPIA, non un modulo condiviso col fornitore. Un jar condiviso fra
 * due servizi sembra far risparmiare codice, e in cambio li lega: cambiare
 * quel jar impone di rilasciare tutti insieme, che e' esattamente cio' che i
 * microservizi servono a evitare. Un database condiviso travestito da
 * dipendenza Maven.
 *
 * Il prezzo della copia: se il fornitore rinomina un campo, ce ne accorgiamo
 * quando arriva un null. Il prezzo del modulo condiviso: ce ne accorgiamo
 * quando non riusciamo piu' a rilasciare da soli. Il primo si paga una volta.
 */
public record MovieJson(String title, int durationMinutes) {
}
