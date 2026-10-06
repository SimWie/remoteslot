-- V7: Index fuer Suche und Auswertungen (T10)
-- Begruendung und Messungen vorher/nachher: docs/performance/README.md
--
-- (beginn, id) entspricht genau der Sortierung der Suche (A6: nach Beginn, dann ID). Damit kann PostgreSQL
--   * die ersten Treffer einer Seite direkt aus dem Index lesen, statt die ganze Tabelle zu sortieren,
--   * Zeitraumfilter (Suche) und Monatsauswertungen (v_einsatz, Filter auf beginn) ueber einen Bereich im Index
--     beantworten, statt alle Reservationen zu lesen.
-- Der Ausgangszustand ohne diesen Index laesst sich mit --spring.flyway.target=6 reproduzieren.

CREATE INDEX ix_reservation_beginn_id ON reservation (beginn, id);
