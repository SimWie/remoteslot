-- T10: Messung der typischen Abfragen (vorher/nachher). Ablauf siehe README.md in diesem Ordner.
-- Jede Abfrage wird dreimal gemessen; die erste Messung kann wegen kaltem Cache langsamer sein.

\pset pager off
SELECT version();
SELECT count(*) AS reservationen FROM reservation;
SELECT indexname FROM pg_indexes WHERE tablename = 'reservation' ORDER BY 1;

\echo
\echo '=== M1: Suche ohne Filter, erste Seite (A6) ==='
EXPLAIN (ANALYZE, BUFFERS) SELECT r.* FROM reservation r ORDER BY r.beginn, r.id LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT r.* FROM reservation r ORDER BY r.beginn, r.id LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT r.* FROM reservation r ORDER BY r.beginn, r.id LIMIT 20;

\echo
\echo '=== M2: Suche nach Zeitraum (eine Woche), erste Seite (A6), wie sie die Anwendung absetzt ==='
EXPLAIN (ANALYZE, BUFFERS) SELECT r.* FROM reservation r
 WHERE r.ende > '2025-03-01T00:00Z' AND r.beginn > TIMESTAMPTZ '2025-03-01T00:00Z' - INTERVAL '8 hours'
   AND r.beginn < '2025-03-08T00:00Z'
 ORDER BY r.beginn, r.id LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT r.* FROM reservation r
 WHERE r.ende > '2025-03-01T00:00Z' AND r.beginn > TIMESTAMPTZ '2025-03-01T00:00Z' - INTERVAL '8 hours'
   AND r.beginn < '2025-03-08T00:00Z'
 ORDER BY r.beginn, r.id LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT r.* FROM reservation r
 WHERE r.ende > '2025-03-01T00:00Z' AND r.beginn > TIMESTAMPTZ '2025-03-01T00:00Z' - INTERVAL '8 hours'
   AND r.beginn < '2025-03-08T00:00Z'
 ORDER BY r.beginn, r.id LIMIT 20;

\echo
\echo '=== M3: Gesamtanzahl fuer dieselbe Suche (COUNT der Pagination) ==='
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM reservation r
 WHERE r.ende > '2025-03-01T00:00Z' AND r.beginn > TIMESTAMPTZ '2025-03-01T00:00Z' - INTERVAL '8 hours'
   AND r.beginn < '2025-03-08T00:00Z';
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM reservation r
 WHERE r.ende > '2025-03-01T00:00Z' AND r.beginn > TIMESTAMPTZ '2025-03-01T00:00Z' - INTERVAL '8 hours'
   AND r.beginn < '2025-03-08T00:00Z';
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM reservation r
 WHERE r.ende > '2025-03-01T00:00Z' AND r.beginn > TIMESTAMPTZ '2025-03-01T00:00Z' - INTERVAL '8 hours'
   AND r.beginn < '2025-03-08T00:00Z';

\echo
\echo '=== M4: Auswertung Aufwand je Kunde fuer einen Monat (A7) ==='
EXPLAIN (ANALYZE, BUFFERS) SELECT k.id, k.name, k.ort, e.monat, count(*), round(sum(e.stunden), 2)
 FROM v_einsatz e JOIN kunde k ON k.id = e.kunde_id
 WHERE e.beginn >= '2025-10-01 00:00+02' AND e.beginn < '2025-11-01 00:00+01'
 GROUP BY k.id, k.name, k.ort, e.monat ORDER BY k.name, k.id, e.monat;
EXPLAIN (ANALYZE, BUFFERS) SELECT k.id, k.name, k.ort, e.monat, count(*), round(sum(e.stunden), 2)
 FROM v_einsatz e JOIN kunde k ON k.id = e.kunde_id
 WHERE e.beginn >= '2025-10-01 00:00+02' AND e.beginn < '2025-11-01 00:00+01'
 GROUP BY k.id, k.name, k.ort, e.monat ORDER BY k.name, k.id, e.monat;
EXPLAIN (ANALYZE, BUFFERS) SELECT k.id, k.name, k.ort, e.monat, count(*), round(sum(e.stunden), 2)
 FROM v_einsatz e JOIN kunde k ON k.id = e.kunde_id
 WHERE e.beginn >= '2025-10-01 00:00+02' AND e.beginn < '2025-11-01 00:00+01'
 GROUP BY k.id, k.name, k.ort, e.monat ORDER BY k.name, k.id, e.monat;
