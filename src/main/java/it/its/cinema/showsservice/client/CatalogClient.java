package it.its.cinema.showsservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * PASSO 6.8b — IL CLIENT HTTP DICHIARATIVO.
 *
 * Non c'e' implementazione, e non va scritta: Feign genera a runtime un proxy
 * che legge queste annotazioni, costruisce la richiesta HTTP, la manda e
 * deserializza la risposta. Si dichiara COSA si chiama, non COME.
 *
 * Confronto con RestClient (passo 6.6), che nel corso usiamo fra i NOSTRI
 * servizi:
 *
 *     // RestClient: il come e' esplicito, riga per riga
 *     List<MovieJson> film = restClient.get()
 *             .uri("/catalog.json")
 *             .retrieve()
 *             .body(new ParameterizedTypeReference<>() {});
 *
 * Feign si legge come un'interfaccia Java e sparisce dalla vista; RestClient
 * e' Spring core, una dipendenza in meno e controllo diretto. Feign conviene
 * quando i contratti remoti sono molti e stabili — un fornitore esterno con
 * venti rotte diventa venti metodi, non venti blocchi di codice.
 *
 * ---------------------------------------------------------------------------
 * "url" E "name" NON SONO LA STESSA COSA, ED E' LA CONFUSIONE DI OGGI.
 *
 *   url  = indirizzo fisso, preso dalla configurazione. Quello che vogliamo.
 *   name = nome LOGICO del servizio. Senza "url", Feign lo passerebbe a un
 *          service discovery (Eureka) per farsi dire dove abita: qui non c'e'
 *          e non serve, e l'avvio fallirebbe.
 *
 * "name" resta obbligatorio anche con "url", perche' identifica il client
 * nella configurazione dei timeout (spring.cloud.openfeign.client.config).
 * ---------------------------------------------------------------------------
 */
@FeignClient(name = "catalog-provider", url = "${cinema.catalog.remote-url}")
public interface CatalogClient {

    /**
     * GET {cinema.catalog.remote-url}/catalog.json
     *
     * produces = "application/json" mette l'header Accept sulla richiesta:
     * e' quello che diciamo al fornitore, non quello che promettiamo noi.
     */
    @GetMapping(value = "/catalog.json", produces = "application/json")
    List<MovieJson> scaricaCatalogo();
}
