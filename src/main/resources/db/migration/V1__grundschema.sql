-- V1: Grundschema RemoteSlot
-- Bereits angewendete Migrationen werden nie mehr veraendert; Aenderungen folgen als V2, V3, ...
-- Bewusst noch keine Zusatzindizes (ausser PK/UNIQUE): Der Ausgangszustand fuer T10 soll
-- reproduzierbar ohne Optimierung bleiben. Indizes folgen begruendet in einer eigenen Migration.

CREATE TABLE kunde (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(120) NOT NULL,
    ort   VARCHAR(80)  NOT NULL,
    CONSTRAINT ck_kunde_name_nicht_leer CHECK (btrim(name) <> '')
);

CREATE TABLE anlage (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kunde_id       BIGINT       NOT NULL REFERENCES kunde (id),
    anlagennummer  VARCHAR(20)  NOT NULL,
    bezeichnung    VARCHAR(120) NOT NULL,
    steuerungstyp  VARCHAR(40)  NOT NULL,
    aktiv          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_anlage_anlagennummer UNIQUE (anlagennummer),
    CONSTRAINT ck_anlage_anlagennummer_format CHECK (anlagennummer ~ '^[A-Z0-9-]{3,20}$')
);

CREATE TABLE techniker (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kuerzel   VARCHAR(6)  NOT NULL,
    vorname   VARCHAR(60) NOT NULL,
    nachname  VARCHAR(60) NOT NULL,
    aktiv     BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_techniker_kuerzel UNIQUE (kuerzel),
    CONSTRAINT ck_techniker_kuerzel_format CHECK (kuerzel ~ '^[A-Z]{2,6}$')
);

CREATE TABLE reservation (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anlage_id     BIGINT       NOT NULL REFERENCES anlage (id),
    techniker_id  BIGINT       NOT NULL REFERENCES techniker (id),
    beginn        TIMESTAMPTZ  NOT NULL,
    ende          TIMESTAMPTZ  NOT NULL,
    zweck         VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'GEPLANT',
    bemerkung     VARCHAR(500),
    version       BIGINT       NOT NULL DEFAULT 0,   -- optimistisches Sperren (T5)
    erstellt_am   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_reservation_zeitraum CHECK (ende > beginn),
    CONSTRAINT ck_reservation_dauer CHECK (ende - beginn BETWEEN INTERVAL '15 minutes' AND INTERVAL '8 hours'),
    CONSTRAINT ck_reservation_zweck CHECK (zweck IN ('STOERUNG', 'UPDATE', 'INBETRIEBNAHME', 'WARTUNG')),
    CONSTRAINT ck_reservation_status CHECK (status IN ('GEPLANT', 'AKTIV', 'ABGESCHLOSSEN', 'STORNIERT'))
    -- "Beginn nicht in der Vergangenheit" ist bewusst eine Regel der Anwendung:
    -- historische Daten (Testdaten, abgeschlossene Einsaetze) muessen speicherbar bleiben.
);

CREATE TABLE statusereignis (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id  BIGINT       NOT NULL REFERENCES reservation (id),
    zeitpunkt       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    alter_status    VARCHAR(20),                       -- NULL beim Erfassen
    neuer_status    VARCHAR(20)  NOT NULL,
    bemerkung       VARCHAR(500),
    CONSTRAINT ck_statusereignis_alter_status CHECK (alter_status IS NULL OR alter_status IN ('GEPLANT', 'AKTIV', 'ABGESCHLOSSEN', 'STORNIERT')),
    CONSTRAINT ck_statusereignis_neuer_status CHECK (neuer_status IN ('GEPLANT', 'AKTIV', 'ABGESCHLOSSEN', 'STORNIERT')),
    CONSTRAINT ck_statusereignis_wechsel CHECK (alter_status IS DISTINCT FROM neuer_status)
);
