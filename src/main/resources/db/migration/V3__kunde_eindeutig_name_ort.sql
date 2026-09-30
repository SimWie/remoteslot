-- V3: Ein Kunde ist durch Name und Ort eindeutig.
-- Derselbe Name an verschiedenen Orten bleibt erlaubt (z. B. Lonza AG in Visp und in Basel).
-- Gross-/Kleinschreibung und Leerzeichen am Rand werden ignoriert (lower, btrim), damit Tippvarianten
-- wie "Lonza AG", "lonza ag" oder "Lonza AG " nicht als zweiter Kunde erfasst werden.
-- Grenze: Abweichende Schreibweisen wie "Lonza AG" und "Lonza" werden nicht erkannt.
-- Der Index dient der Integritaet, nicht der Performance; der Ausgangszustand fuer T10 bleibt unberuehrt.

CREATE UNIQUE INDEX uq_kunde_name_ort ON kunde (lower(btrim(name)), lower(btrim(ort)));
