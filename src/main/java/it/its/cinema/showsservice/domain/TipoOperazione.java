package it.its.cinema.showsservice.domain;

/**
 * PASSO 8.3 — i due tipi di operazione su un sagaId.
 *
 * shows-service non sa che cos'e' una saga (vedi il commento nella V5): sa
 * solo che una coppia (identificativo, tipo) puo' essere eseguita una volta
 * sola. E' tutto cio' che gli serve per essere un buon partecipante.
 */
public enum TipoOperazione {

    /** La riserva dei posti: il primo passo della saga di acquisto. */
    RESERVE,

    /** Il rilascio: la sua compensazione (passo 8.7). */
    RELEASE
}
