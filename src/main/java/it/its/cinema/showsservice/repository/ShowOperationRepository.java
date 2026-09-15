package it.its.cinema.showsservice.repository;

import it.its.cinema.showsservice.domain.ShowOperation;
import it.its.cinema.showsservice.domain.TipoOperazione;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * PASSO 8.3 — "questa operazione l'ho gia' eseguita?".
 *
 * E' il controllo APPLICATIVO dell'idempotenza, e da solo non basta: due
 * chiamate con lo stesso sagaId arrivate insieme rispondono entrambe "no".
 * A decidere davvero e' il vincolo UNIQUE della V5. Serve lo stesso, perche'
 * copre il caso normale — il retry dopo un timeout — senza far scrivere
 * niente al database.
 */
public interface ShowOperationRepository extends JpaRepository<ShowOperation, Long> {

    Optional<ShowOperation> findBySagaIdAndOperationType(String sagaId, TipoOperazione tipo);

    boolean existsBySagaIdAndOperationType(String sagaId, TipoOperazione tipo);
}
