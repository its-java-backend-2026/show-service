package it.its.cinema.showsservice;

import it.its.cinema.showsservice.domain.Movie;
import it.its.cinema.showsservice.domain.NotEnoughSeatsException;
import it.its.cinema.showsservice.domain.Show;
import it.its.cinema.showsservice.repository.InMemoryShowRepository;
import it.its.cinema.showsservice.service.ShowService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ShowsServiceApplicationTests {

	@Test
	void riservareRiduceIPostiDisponibili() {
		InMemoryShowRepository repository = new InMemoryShowRepository();
		ShowService service = new ShowService(repository);

		repository.save(new Show(
				1L,
				new Movie(1L, "Dune", 160),
				LocalDateTime.of(2026, 9, 2, 20, 30),
				BigDecimal.valueOf(10),
				100
		));

		service.getAvailableSeats(1L, 4);

		assertEquals(96, service.findById(1L).getAvailableSeats());
	}

	@Test
	void nonSiPuoRiservarePiuDeiPostiDisponibili() {
		InMemoryShowRepository repository = new InMemoryShowRepository();
		ShowService service = new ShowService(repository);

		repository.save(new Show(
				1L,
				new Movie(1L, "Dune", 160),
				LocalDateTime.of(2026, 9, 2, 20, 30),
				BigDecimal.valueOf(10),
				2
		));

		assertThrows(
				NotEnoughSeatsException.class,
				() -> service.getAvailableSeats(1L, 3)
		);
	}

}
