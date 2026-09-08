-- Dati di esempio, per avere un catalogo su cui lavorare dal primo avvio.
-- Al G4 i film arriveranno da un JSON esterno (passo 4.4) e questa migrazione
-- verra' sostituita: NON modificandola, ma scrivendone un'altra che ripulisce.

INSERT INTO movies (title, minutes) VALUES
    ('Dune - Parte Due', 166),   -- id 1
    ('Oppenheimer',      180),   -- id 2
    ('Perfect Days',     124);   -- id 3

INSERT INTO shows (movie_id, start_time, base_price, total_seats, available_seats) VALUES
    (1, '2026-09-10 17:00:00',  8.50, 120, 120),
    (1, '2026-09-10 21:00:00', 10.00, 120, 118),
    (2, '2026-09-11 20:00:00',  9.00,  80,  80);
