-- V4: Keine Ueberschneidung von Reservationen (Steckbrief A4)
-- Pro Anlage und pro Techniker gibt es zu jedem Zeitpunkt hoechstens eine nicht stornierte Reservation.
-- Umsetzung als Ausschluss-Constraint (EXCLUDE): Die Datenbank prueft beim Schreiben, ob sich der neue
-- Zeitraum mit einem bestehenden ueberschneidet. Das gilt auch, wenn zwei Anfragen gleichzeitig eintreffen
-- (die Pruefung im Service allein kann von beiden bestanden werden).
-- tstzrange(beginn, ende, '[)') ist halboffen: Ende = Beginn ist keine Ueberschneidung (aneinandergrenzende
-- Fenster sind zulaessig). Stornierte Reservationen blockieren nichts (WHERE-Bedingung).
-- btree_gist wird benoetigt, damit die Gleichheit auf anlage_id/techniker_id im GiST-Index geprueft werden kann.
-- Die Constraints legen intern GiST-Indizes an; sie dienen der Integritaet, nicht der Performance (T10).

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservation
    ADD CONSTRAINT ex_reservation_anlage_ueberschneidung
    EXCLUDE USING gist (anlage_id WITH =, tstzrange(beginn, ende, '[)') WITH &&)
    WHERE (status <> 'STORNIERT');

ALTER TABLE reservation
    ADD CONSTRAINT ex_reservation_techniker_ueberschneidung
    EXCLUDE USING gist (techniker_id WITH =, tstzrange(beginn, ende, '[)') WITH &&)
    WHERE (status <> 'STORNIERT');
