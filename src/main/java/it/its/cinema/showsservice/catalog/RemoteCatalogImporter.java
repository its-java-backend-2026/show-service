package it.its.cinema.showsservice.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import feign.FeignException;
import it.its.cinema.showsservice.client.CatalogClient;
import it.its.cinema.showsservice.client.MovieJson;
import it.its.cinema.showsservice.domain.CatalogProviderUnavailableException;
import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PASSO 6.8b — IMPORTARE IL CATALOGO DA UN FORNITORE ESTERNO, CON FEIGN.
 *
 * Il fratello di CatalogImporter, e volutamente separato da lui:
 *
 *     CatalogImporter        legge un FILE, gira UNA volta all'avvio
 *     RemoteCatalogImporter  chiama un SERVIZIO, gira quando lo chiede un umano
 *
 * Le due classi si somigliano nella parte di fusione, e la somiglianza e' un
 * costo accettato con gli occhi aperti. L'alternativa era un importatore
 * condiviso, e avrebbe legato il codice del G4 — che funziona, e' spiegato e
 * sta in un commit — alle esigenze di una sorgente che si comporta in modo
 * diverso: un file c'e' o non c'e', un servizio invece va in timeout, risponde
 * 500, o manda dati sporchi. Il giorno che una delle due sorgenti cambierA'
 * regola, l'altra non se ne accorgera' nemmeno.
 *
 * QUI PASSA IL CONFINE DEL SISTEMA. Sopra questa classe (il controller) non si
 * sa che esista Feign; sotto (CatalogClient) non si sa che esista il nostro
 * dominio. E' l'unico punto in cui le due cose si toccano, ed e' per questo
 * che la traduzione delle eccezioni sta qui e non altrove.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemoteCatalogImporter {

    private final CatalogClient catalogClient;

    private final MovieRepository movieRepository;

    /**
     * Scarica il catalogo del fornitore e importa solo i film che non abbiamo.
     *
     * SENZA IL try/catch, UN FORNITORE SPENTO DIVENTA UN NOSTRO 500.
     *
     * Feign lancia eccezioni UNCHECKED: il compilatore non le nomina e non
     * costringe a gestirle, quindi il try/catch non lo scrivi per obbligo ma
     * per decisione. Le due che vedrai davvero:
     *
     *   FeignException.NotFound      il fornitore risponde 404 (URL sbagliata)
     *   feign.RetryableException     timeout o connessione rifiutata
     *
     * RetryableException ESTENDE FeignException, quindi un catch solo le prende
     * tutte: elencarle entrambe in un multi-catch non compilerebbe
     * ("alternatives related by subclassing"). Il test lo verifica.
     */
    @Transactional
    public EsitoImport importaDalFornitore() {

        List<MovieJson> catalogoRemoto;
        try {
            catalogoRemoto = catalogClient.scaricaCatalogo();
        } catch (FeignException e) {
            // Si logga qui, e a livello WARN: e' un guasto di qualcun altro,
            // non un bug nostro, ma senza questa riga nei log non resta
            // traccia di CHI non ha risposto.
            log.warn("il fornitore del catalogo non ha risposto: {}", e.getMessage());
            throw new CatalogProviderUnavailableException(
                    "Il fornitore del catalogo non ha risposto. Riprova piu' tardi.", e);
        }

        // Una risposta 200 con corpo vuoto non e' un guasto: e' un fornitore
        // che oggi non ha film da offrire. Nessuna eccezione, esito a zero.
        if (catalogoRemoto == null || catalogoRemoto.isEmpty()) {
            log.info("il fornitore ha risposto, ma il catalogo e' vuoto");
            return new EsitoImport(0, 0, 0);
        }

        // L'IMPORT DEVE ESSERE IDEMPOTENTE, come quello da file del passo 4.4:
        // qui il motivo e' persino piu' concreto, perche' la rotta la chiama un
        // umano che cliccherA' due volte. UNA query per i titoli presenti, non
        // existsByTitle dentro al ciclo: quello sarebbe un N+1 scritto a mano.
        Set<String> visti = movieRepository.findAll().stream()
                .map(Movie::getTitle)
                .collect(Collectors.toCollection(HashSet::new));

        // Il ciclo invece di uno stream con filter: "visti" cresce mentre si
        // scorre, e cosi' scarta anche i doppioni INTERNI alla risposta. E' la
        // differenza fra un file, che scriviamo noi, e un servizio di terzi che
        // non ci garantisce nessuna unicita': due righe con lo stesso titolo
        // violerebbero uk_movies_title e farebbero fallire tutto l'import.
        List<Movie> nuovi = new ArrayList<>();
        for (MovieJson film : catalogoRemoto) {
            if (visti.add(film.title())) {
                nuovi.add(new Movie(film.title(), film.durationMinutes()));
            }
        }

        int scartati = catalogoRemoto.size() - nuovi.size();

        if (nuovi.isEmpty()) {
            log.info("catalogo del fornitore gia' allineato: {} film, nessuno nuovo",
                    catalogoRemoto.size());
            return new EsitoImport(0, scartati, catalogoRemoto.size());
        }

        movieRepository.saveAll(nuovi);
        log.info("importati {} film nuovi dal fornitore esterno ({} erano gia' in catalogo)",
                nuovi.size(), scartati);

        return new EsitoImport(nuovi.size(), scartati, catalogoRemoto.size());
    }
}
