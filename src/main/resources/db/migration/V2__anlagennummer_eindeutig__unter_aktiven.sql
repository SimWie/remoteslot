-- V2: Anlagennummer nur noch unter aktiven Anlagen eindeutig
-- Grund: Der Kunde einer Anlage ist unveränderlich. Bei einem Kundenwechsel wird die Anlage
-- ausser Betrieb genommen und beim neuen Kunden mit derselben Anlagennummer neu erfasst.
-- Die alte Anlage bleibt mit ihren Reservationen erhalten, damit Auswertungen je Kunde korrekt bleiben.

ALTER TABLE anlage DROP CONSTRAINT uq_anlage_anlagennummer;
CREATE UNIQUE INDEX uq_anlage_anlagennummer_aktiv ON anlage (anlagennummer) WHERE aktiv = TRUE;