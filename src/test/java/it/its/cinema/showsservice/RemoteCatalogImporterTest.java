package it.its.cinema.showsservice;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import it.its.cinema.showsservice.catalog.EsitoImport;
import it.its.cinema.showsservice.catalog.RemoteCatalogImporter;
import it.its.cinema.showsservice.client.CatalogClient;
import it.its.cinema.showsservice.client.MovieJson;
import it.its.cinema.showsservice.domain.CatalogProviderUnavailableException;
import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.repository.MovieRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PASSO 6.8b — IL TEST DI UNA CHIAMATA REMOTA, SENZA CHIAMARE NIENTE.
 *
 * CatalogClient e' un'interfaccia, e questo e' il regalo di Feign: si sostituisce
 * con un mock come qualunque altra dipendenza. Nessuna rete, nessun container,
 * nessun WireMock — quello arriva al G10, quando servira' verificare anche la
 * forma della richiesta HTTP, non solo il comportamento nostro.
 *
 * Le domande a cui risponde sono due, e sono entrambe sul NOSTRO codice:
 *   - i film che abbiamo gia' non vengono duplicati?
 *   - un fornitore giu' diventa un'eccezione di dominio, non una FeignException
 *     che risale fino al controller?
 */
class RemoteCatalogImporterTest {

    private final CatalogClient client = mock(CatalogClient.class);
    private final MovieRepository movieRepository = mock(MovieRepository.class);

    private final RemoteCatalogImporter service =
            new RemoteCatalogImporter(client, movieRepository);

    @Test
    @DisplayName("importa solo i film che non abbiamo, e non duplica i doppioni interni")
    void importaSoloINuovi() {
        when(movieRepository.findAll()).thenReturn(List.of(
                new Movie("Dune - Parte Due", 166),
                new Movie("Oppenheimer", 180)));

        when(client.scaricaCatalogo()).thenReturn(List.of(
                new MovieJson("Dune - Parte Due", 166),   // gia' nostro
                new MovieJson("Oppenheimer", 180),        // gia' nostro
                new MovieJson("Anora", 139),              // nuovo
                new MovieJson("Conclave", 120),           // nuovo
                new MovieJson("Anora", 139)));            // doppione DEL FORNITORE

        EsitoImport esito = service.importaDalFornitore();

        assertThat(esito.importati()).isEqualTo(2);
        assertThat(esito.giaPresenti()).isEqualTo(3);
        assertThat(esito.totaleNellaSorgente()).isEqualTo(5);
    }

    @Test
    @DisplayName("il fornitore che risponde male diventa un'eccezione di dominio, non una FeignException")
    void erroreHttpTradotto() {
        when(client.scaricaCatalogo()).thenThrow(
                new FeignException.ServiceUnavailable("fornitore in manutenzione",
                        richiestaFinta(), null, Map.<String, Collection<String>>of()));

        assertThatThrownBy(service::importaDalFornitore)
                .isInstanceOf(CatalogProviderUnavailableException.class)
                .hasMessageContaining("Riprova piu' tardi")
                // la causa si conserva: nei log serve sapere cosa e' successo
                .hasRootCauseInstanceOf(FeignException.ServiceUnavailable.class);

        verify(movieRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("anche il timeout diventa la stessa eccezione: RetryableException estende FeignException")
    void timeoutTradotto() {
        when(client.scaricaCatalogo()).thenThrow(
                new RetryableException(-1, "Read timed out", Request.HttpMethod.GET,
                        (Long) null, richiestaFinta()));

        assertThatThrownBy(service::importaDalFornitore)
                .isInstanceOf(CatalogProviderUnavailableException.class);
    }

    @Test
    @DisplayName("un catalogo remoto vuoto non e' un guasto: esito a zero, nessuna scrittura")
    void catalogoVuoto() {
        when(client.scaricaCatalogo()).thenReturn(List.of());

        EsitoImport esito = service.importaDalFornitore();

        assertThat(esito.totaleNellaSorgente()).isZero();
        verify(movieRepository, never()).saveAll(anyList());
    }

    private static Request richiestaFinta() {
        return Request.create(Request.HttpMethod.GET,
                "http://catalog-provider/catalog.json",
                Map.of(), null, new RequestTemplate());
    }
}
