-- V5: Verschieben einer Reservation im Verlauf protokollieren (Steckbrief A5: jede Aenderung erzeugt einen Verlaufseintrag)
-- Bisher verlangte ck_statusereignis_wechsel, dass sich der Status aendert. Beim Verschieben bleibt der Status aber
-- GEPLANT. Neu ist ein Eintrag GEPLANT -> GEPLANT erlaubt, sofern eine Bemerkung (alter und neuer Zeitraum) dabei ist.
-- Andere Eintraege ohne Statuswechsel bleiben verboten (nur GEPLANT kann verschoben werden).

ALTER TABLE statusereignis DROP CONSTRAINT ck_statusereignis_wechsel;

ALTER TABLE statusereignis
    ADD CONSTRAINT ck_statusereignis_wechsel CHECK (
        alter_status IS DISTINCT FROM neuer_status
        OR (alter_status = 'GEPLANT' AND neuer_status = 'GEPLANT' AND bemerkung IS NOT NULL)
    );
