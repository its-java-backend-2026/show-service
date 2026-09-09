-- PASSO 3.7 — la colonna che rende possibile il lock ottimistico.
--
-- DEFAULT 0 e' obbligatorio: le righe gia' esistenti devono avere un valore,
-- altrimenti NOT NULL fallisce sui dati inseriti dalla V2.
ALTER TABLE shows ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- PASSO 3.5 — l'indice a supporto della query "spettacoli di un film fra due date".
-- Senza, PostgreSQL scandisce tutta la tabella: con tre righe non si nota,
-- con trecentomila si'. L'indice va sulle colonne del WHERE, nell'ordine
-- in cui si filtra.
CREATE INDEX idx_shows_movie_start ON shows (movie_id, start_time);
