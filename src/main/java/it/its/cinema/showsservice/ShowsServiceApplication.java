package it.its.cinema.showsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * PASSO 6.8b — @EnableFeignClients.
 *
 * Senza questa annotazione le interfacce @FeignClient non vengono scansionate,
 * nessun proxy viene generato, e l'avvio fallisce con
 *     No qualifying bean of type 'CatalogClient'
 * che non nomina Feign da nessuna parte: e' l'errore piu' comune del passo.
 */
@SpringBootApplication
@EnableFeignClients
public class ShowsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShowsServiceApplication.class, args);
	}

}
