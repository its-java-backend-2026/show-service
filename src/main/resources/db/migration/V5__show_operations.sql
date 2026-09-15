-- ===========================================================================
-- PASSO 8.3 — LA TABELLA CHE RENDE RIPETIBILE UNA RISERVA.
--
-- E' la stessa forma che hanno payment_db e loyalty_db, ed e' lo schema
-- ricorrente di ogni partecipante a una saga:
--
--     tabella *_operations (saga_id, operation_type, ...)
--     CONSTRAINT UNIQUE (saga_id, operation_type)
--
-- ---------------------------------------------------------------------------
-- COSA CAMBIA, DA OGGI, PER CHI CI CHIAMA.
--
-- Fino al G7, in ShowsClient di booking-service, POST /shows/{id}/reserve era
-- l'unico passo SENZA @Retry, e il commento diceva perche': shows-service non
-- riconosceva un sagaId gia' visto, quindi un retry dopo un timeout scalava i
-- posti una seconda volta. "Timeout" non vuol dire "non e' arrivata": vuol
-- dire "non so se e' arrivata", e il piu' delle volte la richiesta era
-- arrivata benissimo ed era la risposta a essersi persa.
--
-- Questa tabella toglie quel vincolo. Da oggi la seconda chiamata con lo
-- stesso saga_id non fa niente e risponde come la prima, quindi il retry
-- diventa sicuro e in booking-service si puo' accendere.
--
-- E' il pezzo di G8 che ripaga un debito del G7 — quello che era stato
-- lasciato scritto nei commenti invece che nascosto.
-- ---------------------------------------------------------------------------
--
-- E ATTENZIONE A UNA COSA CHE QUI NON SI VEDE: shows-service continua a NON
-- sapere che esiste una saga. Riceve un identificativo opaco, lo usa come
-- chiave di idempotenza e lo dimentica. Non conosce i passi, non conosce
-- l'ordine, non sa che esistono un pagamento e dei punti fedelta'. Il
-- coordinamento e' un problema di chi coordina: un partecipante deve solo
-- saper fare — e rifare senza danni — la sua parte.
-- ===========================================================================
CREATE TABLE show_operations (
    id             BIGSERIAL PRIMARY KEY,

    saga_id        VARCHAR(64) NOT NULL,

    -- RESERVE | RELEASE.
    -- Il tipo sta nella chiave perche' la STESSA saga passa di qui due volte
    -- quando compensa: prima riserva, poi rilascia. Con il solo saga_id nel
    -- vincolo, il rilascio sarebbe scambiato per una riserva ripetuta e non
    -- verrebbe eseguito mai — i posti resterebbero bloccati per sempre,
    -- che e' esattamente il guasto che la saga esiste per evitare.
    operation_type VARCHAR(20) NOT NULL,

    show_id        BIGINT      NOT NULL,

    quantity       INTEGER     NOT NULL,

    created_at     TIMESTAMP   NOT NULL,

    CONSTRAINT uk_show_operations_saga_tipo UNIQUE (saga_id, operation_type),

    CONSTRAINT ck_show_operations_quantity_positiva CHECK (quantity > 0),

    -- La FOREIGN KEY verso shows qui c'e', e non e' una contraddizione con il
    -- passo 6.2: shows e show_operations stanno nello STESSO database, di
    -- proprieta' dello STESSO servizio. L'integrita' referenziale si perde
    -- FRA servizi, non dentro. Dove si puo' avere, si tiene.
    CONSTRAINT fk_show_operations_show FOREIGN KEY (show_id) REFERENCES shows (id)
);

-- "Tutte le operazioni su questo spettacolo" e' la domanda di chi indaga su
-- una disponibilita' che non torna. Il vincolo UNIQUE indicizza
-- (saga_id, operation_type) e non aiuta su show_id: questo indice serve.
CREATE INDEX idx_show_operations_show ON show_operations (show_id);
