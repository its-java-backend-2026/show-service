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
 *
 * ===========================================================================
 * NOTA ONESTA: FEIGN E' IN MAINTENANCE MODE.
 *
 * Spring Cloud OpenFeign non riceve funzionalita' nuove dal 2022 (train
 * 2022.0): solo correzioni di bug e patch di sicurezza. Funziona, e' supportato
 * e la base installata e' enorme — lo incontrerete nel codice esistente, ed e'
 * per questo che sta in questo corso. Su un progetto NUOVO, pero', oggi non e'
 * piu' la scelta predefinita.
 *
 * Lo stile dichiarativo — l'unica vera ragione per preferirlo a RestClient —
 * adesso ce l'ha Spring core: si chiamano HTTP INTERFACES, e in Spring
 * Framework 7 (cioe' in QUESTO progetto, senza aggiungere niente al pom) la
 * stessa interfaccia si scrive cosi':
 *
 *     @HttpExchange(url = "/catalog.json", accept = "application/json")
 *     public interface CatalogClient {
 *         @GetExchange
 *         List<MovieJson> scaricaCatalogo();
 *     }
 *
 * e si registra con una riga al posto di @EnableFeignClients:
 *
 *     @ImportHttpServices(group = "catalogo", types = CatalogClient.class)
 *
 * Cosa si guadagna: sparisce TUTTO spring-cloud-dependencies dal pom — niente
 * BOM, niente release train da tenere allineato a Boot, che e' la terza delle
 * tre trappole elencate qui sopra. Sotto il proxy c'e' un RestClient normale,
 * quindi i timeout restano dove li mette il passo 6.6.
 *
 * La regola da portarsi a casa non e' "usate Feign". E':
 *   lo stile dichiarativo conviene quando le rotte remote sono MOLTE e
 *   STABILI e gli errori si trattano TUTTI ALLO STESSO MODO. Oggi quello
 *   stile si ottiene con le HTTP Interfaces; Feign lo si tiene dove c'e' gia'.
 * ===========================================================================
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
