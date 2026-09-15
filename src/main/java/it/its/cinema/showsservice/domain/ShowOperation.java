package it.its.cinema.showsservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PASSO 8.3 — L'OPERAZIONE GIA' ESEGUITA SU UNO SPETTACOLO.
 *
 * E' cio' che permette di rispondere alla domanda "questa riserva l'ho gia'
 * fatta?" — che senza una riga da qualche parte non ha risposta: la
 * disponibilita' di uno spettacolo e' un contatore, e un contatore non si
 * ricorda come ci e' arrivato.
 *
 * NESSUN riferimento a Movie e nessun @ManyToOne verso Show: si tiene il
 * showId come numero. E' una tabella di servizio, che si scrive a ogni
 * riserva e non si legge quasi mai — caricarle dietro un'entita' intera
 * sarebbe lavoro sprecato a ogni prenotazione.
 */
@Entity
@Table(name = "show_operations")
@Getter
@NoArgsConstructor
public class ShowOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private TipoOperazione operationType;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ShowOperation(String sagaId, TipoOperazione tipo, Long showId, int quantity) {
        if (sagaId == null || sagaId.isBlank()) {
            throw new IllegalArgumentException("Il sagaId e' obbligatorio");
        }
        if (sagaId.length() > 64) {
            throw new IllegalArgumentException("Il sagaId non puo' superare i 64 caratteri");
        }
        if (showId == null) {
            throw new IllegalArgumentException("Lo spettacolo e' obbligatorio");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantita' deve essere positiva");
        }
        this.sagaId = sagaId;
        this.operationType = tipo;
        this.showId = showId;
        this.quantity = quantity;
        this.createdAt = LocalDateTime.now();
    }
}
