package it.its.cinema.showsservice.domain;

/**
 * PASSO 6.8b — IL FORNITORE DEL CATALOGO NON HA RISPOSTO.
 *
 * Un'eccezione DI DOMINIO, e non FeignException lasciata risalire. Il motivo
 * e' lo stesso dei DTO al passo 4.1: la libreria che usiamo per chiamare
 * qualcun altro non deve arrivare fino al confine HTTP. Se domani Feign
 * diventasse RestClient, GestoreErrori non cambierebbe di una riga.
 *
 * E il codice che ne esce e' 503, non 500: 500 vuol dire "colpa nostra, un
 * bug"; qui il nostro codice ha funzionato perfettamente ed e' il fornitore
 * che non c'era. La differenza e' operativa, non estetica — un 500 fa
 * svegliare noi di notte, un 503 dice al client "riprova piu' tardi".
 */
public class CatalogProviderUnavailableException extends RuntimeException {

    public CatalogProviderUnavailableException(String messaggio, Throwable causa) {
        super(messaggio, causa);
    }
}