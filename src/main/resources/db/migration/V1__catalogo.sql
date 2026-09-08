-- PASSO 2.4 — lo schema iniziale.
--
-- REGOLA: una migrazione gia' applicata NON SI MODIFICA MAI. Flyway ne
-- registra il checksum in flyway_schema_history; cambiarne anche solo uno
-- spazio fa fallire l'avvio successivo con "Migration checksum mismatch".
-- Se serve un cambiamento, se ne scrive un'altra (V2, V3, ...).

CREATE TABLE movies (
    id      BIGSERIAL PRIMARY KEY,
    title   VARCHAR(200) NOT NULL,
    minutes INT          NOT NULL,
    CONSTRAINT uk_movies_title UNIQUE (title)
);

-- I vincoli CHECK stanno nel database, non solo in Java: il database e'
-- l'ultima linea di difesa, e regge anche quando i dati arrivano da uno
-- script, da un altro servizio o da un collega con psql aperto.
CREATE TABLE shows (
    id              BIGSERIAL     PRIMARY KEY,
    movie_id        BIGINT        NOT NULL REFERENCES movies (id),
    start_time      TIMESTAMP     NOT NULL,
    base_price      NUMERIC(8, 2) NOT NULL CHECK (base_price >= 0),
    total_seats     INT           NOT NULL CHECK (total_seats > 0),
    available_seats INT           NOT NULL CHECK (available_seats >= 0)
);
