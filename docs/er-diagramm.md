# ER-Diagramm

```mermaid
erDiagram
    KUNDE ||--o{ ANLAGE : besitzt
    ANLAGE ||--o{ RESERVATION : "wird reserviert"
    TECHNIKER ||--o{ RESERVATION : "fuehrt aus"
    RESERVATION ||--|{ STATUSEREIGNIS : protokolliert

    KUNDE {
        bigint id PK
        varchar name
        varchar ort
    }
    ANLAGE {
        bigint id PK
        bigint kunde_id FK
        varchar anlagennummer UK
        varchar bezeichnung
        varchar steuerungstyp
        boolean aktiv
    }
    TECHNIKER {
        bigint id PK
        varchar kuerzel UK
        varchar vorname
        varchar nachname
        boolean aktiv
    }
    RESERVATION {
        bigint id PK
        bigint anlage_id FK
        bigint techniker_id FK
        timestamptz beginn
        timestamptz ende
        varchar zweck
        varchar status
        varchar bemerkung
        bigint version
        timestamptz erstellt_am
    }
    STATUSEREIGNIS {
        bigint id PK
        bigint reservation_id FK
        timestamptz zeitpunkt
        varchar alter_status
        varchar neuer_status
        varchar bemerkung
    }
```

## Schemaentscheidungen

- **Kunde nicht in der Reservation:** wird über die Anlage abgeleitet (3. Normalform).
- **Aktueller Status redundant in `reservation`:** bewusst, damit Suche und Filter nicht jedes Mal den Verlauf auswerten müssen. Status und Statusereignis werden immer in derselben Transaktion geschrieben.
- **CHECK-Constraints** für Status, Zweck, Zeitraum und Dauer: Die Regeln gelten auch bei Zugriffen ohne API (z. B. Testdatenskript).
- **`beginn` in der Zukunft** ist nur eine Anwendungsregel, weil historische Daten speicherbar bleiben müssen.
- **`timestamptz`** statt `timestamp`: eindeutige Zeitpunkte, auch über die Sommerzeitumstellung.
- **Keine Zusatzindizes in V1**, damit der Ausgangszustand für T10 reproduzierbar bleibt.
