-- T9: Reproduzierbare Testdaten fuer RemoteSlot
--
-- Aufruf (lokale Compose-Datenbank, Anwendung vorher einmal gestartet, damit Flyway das Schema angelegt hat):
--   docker compose exec -T postgres psql -U remoteslot -d remoteslot < scripts/testdaten.sql
--   (oder mit lokalem psql: psql -h localhost -U remoteslot -d remoteslot -f scripts/testdaten.sql)
--
-- Umfang (Steckbrief): ca. 150 Kunden, 1'000 Anlagen, 40 Techniker, rund 150'000 Reservationen ueber drei Jahre
-- mit zugehoerigen Statusereignissen. Alle Namen sind synthetisch.
--
-- Reproduzierbar: setseed() legt den Zufallsgenerator fest, und der Stichtag ("heute" fuer die Daten) ist fix.
-- Zwei Laeufe erzeugen damit dieselben Daten, unabhaengig vom Tag, an dem das Skript laeuft.
--
-- Bewusst ungleichmaessig verteilt, damit Filter, Indizes und Auswertungen aussagekraeftig sind:
--   * Wenige Kunden besitzen viele Anlagen (Zuordnung ueber random()^3).
--   * Werktags viele Reservationen, am Wochenende nur vereinzelte.
--   * Status abhaengig vom Stichtag: Vergangenheit ueberwiegend ABGESCHLOSSEN, Zukunft ueberwiegend GEPLANT,
--     dazwischen STORNIERT; einzelne "Problemanlagen" mit hohem Anteil an STOERUNG.
--
-- Ueberschneidungsfrei (V4): Der Tag ist in sechs Zeitfenster von zwei Stunden geteilt. Innerhalb eines Fensters
-- erhaelt jede Reservation einen anderen Techniker und eine andere Anlage, und jede Reservation dauert hoechstens
-- zwei Stunden. Damit kann es keine Ueberschneidung je Anlage oder je Techniker geben.

\set ON_ERROR_STOP on

BEGIN;

TRUNCATE statusereignis, reservation, anlage, kunde, techniker RESTART IDENTITY CASCADE;

SELECT setseed(0.42);

-- Stichtag: Reservationen davor sind "vergangen", danach "geplant"
CREATE TEMP TABLE param ON COMMIT DROP AS
SELECT TIMESTAMPTZ '2026-10-15 00:00 Europe/Zurich' AS stichtag,
       DATE '2024-04-01'                             AS erster_tag,
       1095                                          AS anzahl_tage;

-- ---------------------------------------------------------------------------------------------------------
-- Kunden (150)
-- ---------------------------------------------------------------------------------------------------------
INSERT INTO kunde (name, ort)
SELECT (ARRAY['Alpen', 'Rhein', 'Aare', 'Jura', 'Linth', 'Reuss', 'Emme', 'Thur', 'Saane', 'Birs'])[1 + i % 10]
           || ' ' || (ARRAY['Logistik', 'Verpackung', 'Pharma', 'Food', 'Metall', 'Kunststoff'])[1 + (i / 10) % 6]
           || ' AG ' || lpad(i::text, 3, '0'),
       (ARRAY['Bern', 'Basel', 'Zuerich', 'Luzern', 'St. Gallen', 'Visp', 'Thun', 'Biel', 'Chur', 'Aarau'])[1 + (i * 7) % 10]
FROM generate_series(1, 150) AS i;

-- ---------------------------------------------------------------------------------------------------------
-- Anlagen (1'000): wenige Kunden mit vielen Anlagen; jede 50. Anlage ist ausser Betrieb
-- ---------------------------------------------------------------------------------------------------------
INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp, aktiv)
SELECT 1 + floor(150 * power(random(), 3))::int,
       'ANL-' || lpad(i::text, 5, '0'),
       (ARRAY['Palettierer', 'Hochregallager', 'Foerderanlage', 'Verpackungslinie', 'Sortieranlage', 'Kommissionierung'])[1 + i % 6]
           || ' ' || (1 + i % 9),
       (ARRAY['S7-1500', 'S7-1500', 'S7-1200', 'S7-300', 'CODESYS', 'Beckhoff'])[1 + floor(random() * 6)::int],
       i % 50 <> 0
FROM generate_series(1, 1000) AS i;

-- ---------------------------------------------------------------------------------------------------------
-- Techniker (40), Kuerzel TAA, TAB, ...; zwei sind deaktiviert
-- ---------------------------------------------------------------------------------------------------------
INSERT INTO techniker (kuerzel, vorname, nachname, aktiv)
SELECT 'T' || chr(65 + i / 26) || chr(65 + i % 26),
       (ARRAY['Anna', 'Beat', 'Carla', 'Dario', 'Elena', 'Fabian', 'Gina', 'Hugo'])[1 + i % 8],
       (ARRAY['Muster', 'Beispiel', 'Probst', 'Vogel', 'Keller', 'Frei', 'Graf', 'Huber', 'Roth', 'Senn'])[1 + i % 10],
       i < 38
FROM generate_series(0, 39) AS i;

-- ---------------------------------------------------------------------------------------------------------
-- Reservationen (~150'000)
-- ---------------------------------------------------------------------------------------------------------
CREATE TEMP TABLE slot ON COMMIT DROP AS
SELECT d, s, tag,
       (tag + make_interval(hours => 6 + 2 * s)) AT TIME ZONE 'Europe/Zurich' AS fensterbeginn,
       CASE WHEN extract(isodow FROM tag) < 6
            THEN 25 + floor(random() * 16)::int     -- werktags 25-40 Reservationen je Fenster
            ELSE floor(random() * 3)::int           -- am Wochenende 0-2
       END AS anzahl,
       floor(random() * 1000)::int AS anlage_start
FROM param,
     generate_series(0, param.anzahl_tage - 1) AS d,
     generate_series(0, 5) AS s,
     LATERAL (SELECT param.erster_tag + d AS tag) AS t;

CREATE TEMP TABLE neu ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY slot.d, slot.s, j)               AS nr,
       -- innerhalb eines Fensters verschiedene Anlagen (Schrittweite 383 ist teilerfremd zu 1000)
       1 + (slot.anlage_start + j * 383) % 1000                    AS anlage_id,
       -- innerhalb eines Fensters verschiedene Techniker (rotierend, damit alle gleichmaessig arbeiten)
       1 + (j + slot.d * 7 + slot.s) % 40                           AS techniker_id,
       slot.fensterbeginn + make_interval(mins => (ARRAY[0, 0, 15, 30])[1 + floor(random() * 4)::int]) AS beginn,
       (ARRAY[30, 45, 60, 60, 90])[1 + floor(random() * 5)::int]    AS minuten,
       random()                                                     AS z_zweck,
       random()                                                     AS z_status,
       1 + floor(random() * 30)::int                                AS tage_vorlauf
FROM slot
CROSS JOIN LATERAL generate_series(0, slot.anzahl - 1) AS j;

INSERT INTO reservation (anlage_id, techniker_id, beginn, ende, zweck, status, bemerkung, version, erstellt_am)
SELECT n.anlage_id,
       n.techniker_id,
       n.beginn,
       n.beginn + make_interval(mins => n.minuten),
       CASE
           -- Problemanlagen (jede 37.): ueberwiegend Stoerungen
           WHEN n.anlage_id % 37 = 0 AND n.z_zweck < 0.6 THEN 'STOERUNG'
           WHEN n.z_zweck < 0.30 THEN 'STOERUNG'
           WHEN n.z_zweck < 0.55 THEN 'UPDATE'
           WHEN n.z_zweck < 0.85 THEN 'WARTUNG'
           ELSE 'INBETRIEBNAHME'
       END,
       st.status,
       CASE WHEN st.status = 'STORNIERT' AND NOT a.aktiv AND n.beginn >= p.stichtag
            THEN 'Anlage ausser Betrieb genommen' END,
       CASE st.status WHEN 'ABGESCHLOSSEN' THEN 2 WHEN 'STORNIERT' THEN 1 ELSE 0 END,
       n.beginn - make_interval(days => n.tage_vorlauf)
FROM neu n
JOIN anlage a ON a.id = n.anlage_id
CROSS JOIN param p
CROSS JOIN LATERAL (
    SELECT CASE
               WHEN n.beginn < p.stichtag THEN CASE WHEN n.z_status < 0.85 THEN 'ABGESCHLOSSEN' ELSE 'STORNIERT' END
               WHEN NOT a.aktiv           THEN 'STORNIERT'   -- Anlage ausser Betrieb: kuenftige storniert (T4)
               ELSE CASE WHEN n.z_status < 0.92 THEN 'GEPLANT' ELSE 'STORNIERT' END
           END AS status
) AS st
ORDER BY n.nr;

-- ---------------------------------------------------------------------------------------------------------
-- Statusereignisse: passender Verlauf je Reservation
--   GEPLANT:        Erfassung
--   ABGESCHLOSSEN:  Erfassung, GEPLANT -> AKTIV (Beginn), AKTIV -> ABGESCHLOSSEN (Ende)
--   STORNIERT:      Erfassung, GEPLANT -> STORNIERT (vor dem Beginn)
-- ---------------------------------------------------------------------------------------------------------
INSERT INTO statusereignis (reservation_id, zeitpunkt, alter_status, neuer_status, bemerkung)
SELECT id, erstellt_am, NULL, 'GEPLANT', NULL
FROM reservation
UNION ALL
SELECT id, beginn, 'GEPLANT', 'AKTIV', NULL
FROM reservation WHERE status = 'ABGESCHLOSSEN'
UNION ALL
SELECT id, ende, 'AKTIV', 'ABGESCHLOSSEN', NULL
FROM reservation WHERE status = 'ABGESCHLOSSEN'
UNION ALL
SELECT id, erstellt_am + (beginn - erstellt_am) / 2, 'GEPLANT', 'STORNIERT', bemerkung
FROM reservation WHERE status = 'STORNIERT'
ORDER BY 1, 2;

COMMIT;

-- Statistiken fuer den Query-Planer aktualisieren (wichtig fuer T10)
ANALYZE;

-- Kontrolle
SELECT 'kunde' AS tabelle, count(*) FROM kunde
UNION ALL SELECT 'anlage', count(*) FROM anlage
UNION ALL SELECT 'techniker', count(*) FROM techniker
UNION ALL SELECT 'reservation', count(*) FROM reservation
UNION ALL SELECT 'statusereignis', count(*) FROM statusereignis;
