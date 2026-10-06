# Performanceanalyse (T10)

## Fragestellung

Wie schnell sind die typischen Abfragen der Anwendung bei realistischer Datenmenge, und welcher Index lohnt sich?
Untersucht werden die Suche (A6) und die Monatsauswertung (A7), weil sie am häufigsten laufen und ohne Index
die ganze Tabelle `reservation` lesen müssen.

## Datenmenge

Testdaten aus `scripts/testdaten.sql` (T9): 150 Kunden, 1'000 Anlagen, 40 Techniker, 154'569 Reservationen,
398'984 Statusereignisse. Tabelle `reservation` ca. 17 MB.

## Gemessene Abfragen

Das genaue SQL steht in [`messung.sql`](messung.sql); jede Abfrage wird dreimal mit `EXPLAIN (ANALYZE, BUFFERS)` gemessen.

| Nr. | Abfrage | Entspricht |
|---|---|---|
| M1 | Suche ohne Filter, erste Seite (20 Treffer, sortiert nach Beginn und ID) | `GET /api/reservationen` |
| M2 | Suche nach Zeitraum (eine Woche), erste Seite | `GET /api/reservationen?von=…&bis=…` |
| M3 | Gesamtanzahl zur Suche M2 (COUNT der Pagination) | `gesamtanzahl` in der Antwort |
| M4 | Aufwand je Kunde für einen Monat (View `v_einsatz`) | `GET /api/auswertungen/aufwand-kunde` |

## Massnahme

**Index `ix_reservation_beginn_id` auf `reservation (beginn, id)`** (Migration V7).

- Die Spalten entsprechen genau der Sortierung der Suche. PostgreSQL kann die ersten 20 Treffer direkt aus dem
  Index lesen, statt alle Zeilen zu sortieren (M1).
- Zeitraumfilter und Monatsauswertung filtern auf `beginn` und können einen Bereich im Index lesen (M2–M4).
- **Anpassung der Suche:** Die Bedingung „`ende` nach `von`“ kann ein Index auf `beginn` nicht eingrenzen; PostgreSQL
  würde den Index vom Anfang her durchlesen (in einer Vormessung *mehr* gelesene Seiten als ohne Index).
  Weil eine Reservation höchstens 8 Stunden dauert (CHECK in V1), gilt aus `ende > von` immer auch
  `beginn > von − 8 h`. Diese fachlich redundante Bedingung ergänzt die Suche
  (`ReservationSpecifications#abZeitpunkt`); sie gibt dem Index eine Untergrenze.

## Ablauf der Messung (reproduzierbar)

```bash
# 1. Datenbank neu, Schema nur bis V6 (Ausgangszustand ohne Index)
docker compose down -v && docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.flyway.target=6   # nach dem Start mit Ctrl+C beenden

# 2. Testdaten laden und messen
docker compose exec -T postgres psql -U remoteslot -d remoteslot < scripts/testdaten.sql
docker compose exec -T postgres psql -U remoteslot -d remoteslot < docs/performance/messung.sql > docs/performance/messung-vorher.txt

# 3. Anwendung normal starten (Flyway spielt V7 ein), beenden, Statistiken aktualisieren, nochmals messen
./mvnw spring-boot:run                                                          # nach dem Start mit Ctrl+C beenden
docker compose exec -T postgres psql -U remoteslot -d remoteslot -c "ANALYZE"
docker compose exec -T postgres psql -U remoteslot -d remoteslot < docs/performance/messung.sql > docs/performance/messung-nachher.txt
```

## Ergebnisse

### Umgebung

| | |
|---|---|
| Hardware | *ergänzen: Mac-Modell, CPU, RAM (Apple-Menü → Über diesen Mac)* |
| PostgreSQL | *ergänzen: erste Zeile in `messung-vorher.txt`* (Docker, `postgres:17-alpine`) |
| Docker | *ergänzen: `docker --version`* |

### Messwerte (Ausführungszeit, Median aus 3 Messungen)

| | vorher | nachher | Plan vorher → nachher |
|---|---|---|---|
| M1 Suche, erste Seite | *…* ms | *…* ms | *…* |
| M2 Suche Zeitraum | *…* ms | *…* ms | *…* |
| M3 Gesamtanzahl | *…* ms | *…* ms | *…* |
| M4 Auswertung Monat | *…* ms | *…* ms | *…* |

Rohdaten: [`messung-vorher.txt`](messung-vorher.txt), [`messung-nachher.txt`](messung-nachher.txt)

### Vormessung (Claude, zur Auswahl des Index)

Zur Auswahl des Index wurde dieselbe `messung.sql` vorab in einer anderen Umgebung ausgeführt
(PostgreSQL 16.13, Linux, 2 vCPU, 8 GB RAM, gleiche Testdaten). Die Werte sind nicht direkt mit den eigenen
Messungen oben vergleichbar, zeigen aber dieselbe Tendenz.

| | vorher | nachher | Plan vorher → nachher |
|---|---|---|---|
| M1 Suche, erste Seite | 15.5 ms | 0.015 ms | Parallel Seq Scan + Sort → Index Scan (liest nur 20 Zeilen) |
| M2 Suche Zeitraum | 9.7 ms | 0.017 ms | Parallel Seq Scan → Index Scan mit Bereich auf `beginn` |
| M3 Gesamtanzahl | 9.4 ms | 0.21 ms | Parallel Seq Scan → Index Scan |
| M4 Auswertung Monat | 14.7 ms | 5.5 ms | Seq Scan → Bitmap Index Scan |

Indexgrösse: ca. 4.8 MB (Tabelle ca. 17 MB).

## Interpretation

*Nach den eigenen Messungen ergänzen. Stichworte:*

- Ohne Index liest jede Suche alle ~155'000 Reservationen und sortiert sie, auch wenn nur 20 Treffer angezeigt werden.
  Die Laufzeit wächst mit der Tabellengrösse; mit dem Index bleibt die erste Seite praktisch konstant schnell.
- M4 profitiert weniger stark, weil nach dem Index noch rund 4'000 Zeilen gelesen, gejoint und aggregiert werden.
- **Kosten des Index:** zusätzlicher Speicher (~4.8 MB) und etwas mehr Aufwand bei jedem INSERT/UPDATE einer Reservation.
  Bei dieser Anwendung (viel mehr Lesen als Schreiben) lohnt sich das.
- **Grenzen:**
  - Sehr hohe Seitenzahlen (`OFFSET` 100'000) bleiben langsam, weil PostgreSQL alle übersprungenen Zeilen trotzdem lesen
    muss. Abhilfe wäre Keyset-Pagination („Seite nach Beginn X / ID Y“), hier nicht umgesetzt.
  - Bei der Suche nach einem Kunden mit sehr wenigen Anlagen kann der Planer den Index in Sortierreihenfolge ablaufen
    und dabei viele Zeilen prüfen; ein zusätzlicher Index auf `reservation (anlage_id)` wäre eine mögliche weitere Massnahme.
