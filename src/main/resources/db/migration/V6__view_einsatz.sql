-- V6: View als gemeinsame Grundlage fuer die Auswertungen (A7, T6)
-- Eine Zeile je abgeschlossener Reservation ("Einsatz") mit allem, was die Auswertungen brauchen.
--
-- Zaehlregeln aus dem Steckbrief:
--   * Es zaehlen nur Reservationen im Status ABGESCHLOSSEN.
--   * Die Dauer entspricht dem Zeitraum von Beginn bis Ende (in Stunden).
--   * Eine Reservation wird dem Monat ihres Beginns zugeordnet, und zwar in Schweizer Zeit:
--     Ein Einsatz am 01.11. um 00:30 (Europe/Zurich) ist in UTC noch der 31.10. und gehoert trotzdem zum November.
--
-- Keine Doppelzaehlung: Der Join auf anlage ist n:1 (jede Reservation hat genau eine Anlage), die Zeilenzahl
-- bleibt also gleich. Der Verlauf (statusereignis, 1:n) wird bewusst NICHT gejoint; er wuerde jede Reservation
-- so oft zaehlen, wie sie Verlaufseintraege hat.

CREATE VIEW v_einsatz AS
SELECT r.id                                                        AS reservation_id,
       r.anlage_id,
       a.kunde_id,
       r.techniker_id,
       r.zweck,
       r.beginn,
       r.ende,
       EXTRACT(EPOCH FROM (r.ende - r.beginn)) / 3600.0            AS stunden,
       date_trunc('month', r.beginn AT TIME ZONE 'Europe/Zurich')::date AS monat
FROM reservation r
JOIN anlage a ON a.id = r.anlage_id
WHERE r.status = 'ABGESCHLOSSEN';
