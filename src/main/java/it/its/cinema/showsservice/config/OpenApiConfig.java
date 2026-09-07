package it.its.cinema.showsservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * L'intestazione della Swagger UI.
 *
 * Non e' obbligatorio: senza, springdoc funziona lo stesso ma la pagina si
 * intitola "OpenAPI definition". Dal G6, quando i servizi diventano sei e
 * ognuno ha la sua Swagger UI aperta in una scheda, il titolo giusto in cima
 * e' l'unico modo per capire quale si sta guardando.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI showsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("shows-service")
                .version("1.0.0")
                .description("Catalogo film e spettacoli, disponibilita' dei posti."));
    }
}
