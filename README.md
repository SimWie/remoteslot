# RemoteSlot – Reservation von Fernwartungszugängen

Projektarbeit Database Development (HFTM). Spring Boot, JPA, PostgreSQL, Flyway.

> Abgegebener Stand: Git-Tag `abgabe-v1` *(bei Abgabe setzen)*

## Thema

Techniker reservieren verbindlich Zeitfenster für den Fernwartungszugang auf Kundenanlagen. Überlappende Zugriffe auf dieselbe Anlage werden verhindert, Statusänderungen werden protokolliert, und der Fernwartungsaufwand lässt sich je Kunde und Techniker auswerten. Details: siehe Projektsteckbrief.

## Voraussetzungen

- Java 21
- Maven 3.9+ (oder Maven Wrapper, siehe unten)
- Docker (für die lokale Datenbank und für Testcontainers)

Einmalig den Maven Wrapper erzeugen, danach genügt `./mvnw`:

```bash
mvn -N wrapper:wrapper
```

## Start

```bash
docker compose up -d                 # PostgreSQL auf localhost:5432
./mvnw spring-boot:run               # Flyway baut das Schema beim Start auf
```

Swagger UI: http://localhost:8080/swagger-ui.html
Beispielaufrufe: Ordner [`http/`](http/) (IntelliJ HTTP Client oder VS Code REST Client).

Datenbank komplett neu aufbauen:

```bash
docker compose down -v && docker compose up -d && ./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```

Die Tests starten über Testcontainers ein eigenes PostgreSQL 17 (Docker muss laufen). Die lokale Compose-Datenbank wird nicht verwendet.

## Testdaten (T9)

Die Anwendung einmal starten (Flyway legt das Schema an), dann:

```bash
docker compose exec -T postgres psql -U remoteslot -d remoteslot < scripts/testdaten.sql
```

(Mit lokal installiertem `psql` alternativ: `psql -h localhost -U remoteslot -d remoteslot -f scripts/testdaten.sql`)

Erzeugt reproduzierbar (fester Seed und Stichtag 15.10.2026) 150 Kunden, 1'000 Anlagen, 40 Techniker, rund 155'000 Reservationen (April 2024 bis März 2027) und rund 400'000 Statusereignisse. Laufzeit etwa 20 Sekunden. Bestehende Daten werden vorher gelöscht.

## ER-Diagramm

Siehe [`docs/er-diagramm.md`](docs/er-diagramm.md).

## Nachweise

| Anforderung | Umsetzung | Nachweis |
|---|---|---|
| A1 Kunden und Anlagen verwalten | `kunde/`, `anlage/`, `V2`, `V3` | `KundeApiTest`, `AnlageApiTest`, `http/kunde.http`, `http/anlage.http` |
| A2 Techniker verwalten | `techniker/` | `TechnikerApiTest`, `http/techniker.http` |
| A3 Fernwartungsfenster reservieren | `reservation/ReservationService#anlegen` | `ReservationApiTest`, `http/reservation.http` |
| A4 Überschneidungen verhindern | `V4__reservation_keine_ueberschneidung.sql` (Ausschluss-Constraints), Vorabprüfung im Service | `ReservationApiTest` (A4-Tests), `SchemaMigrationTest` (V4-Tests) |
| A5 Status ändern, verschieben, stornieren | `Reservation#statusWechseln`, `#verschieben`, `V5__verlauf_verschieben.sql` | `ReservationAenderungTest` |
| A6 Reservationen suchen | `ReservationSpecifications`, `GET /api/reservationen` | `ReservationSucheTest` |
| A7 Fernwartungsaufwand auswerten | `V6__view_einsatz.sql`, `auswertung/` | `AuswertungTest`, `http/auswertung.http` |
| T1 Datenmodell und Integrität | `V1`–`V6`, `docs/er-diagramm.md` | `SchemaMigrationTest` (Regeln direkt in der DB, ohne API) |
| T2 Migrationen | `db/migration/` (V1–V6) | `SchemaMigrationTest` |
| T3 Anwendung und JPA | Entities, Repositories, Services je Paket; `reservation/Reservation`, `Statusereignis` | `ReservationMappingTest`, alle API-Tests |
| T4 Transaktion und Rollback | `AnlageService#ausserBetriebNehmen` + `ReservationService#stornierenWegenAusserbetriebnahme` | `AnlageAusserBetriebTest#beiLaufenderReservationWirdAllesZurueckgenommen` |
| T5 Gleichzeitige Änderungen | Ausschluss-Constraints (V4), `@Version` + Versionsprüfung, `FOR SHARE`/`FOR UPDATE` auf Anlage und Techniker | `ReservationApiTest#gleichzeitigeDoppelreservationErgibtGenauEineReservation`, `ReservationAenderungTest#aenderungAufVeraltetemStandWirdAbgelehnt`, `ReservationMappingTest` |
| T6 Query Design, JDBC, View | View `v_einsatz` (V6), `AuswertungRepository` (JDBC) | `AuswertungTest` |
| T7 Filter, Sortierung, Pagination | `ReservationSpecifications`, Sortierung Beginn + ID, `Seite`-DTO | `ReservationSucheTest` |
| T8 Integrationstests | `src/test/` (Testcontainers, PostgreSQL 17) | `./mvnw test` |
| T9 Testdaten | `scripts/testdaten.sql` | Kontrollabfrage am Ende des Skripts; reproduzierbar (gleiche Prüfsumme bei wiederholtem Lauf) |
| T10 Performance und Index | `V7__index_reservation_beginn.sql`, Untergrenze in `ReservationSpecifications#abZeitpunkt` | `docs/performance/README.md`, `docs/performance/messung.sql` |
| T11 ORM-Ladeverhalten | Profil `sqllog` | – |
| T12 Cache und Aktualität | – | – |

## Einschränkungen

- **Eindeutigkeit Kunde und Anlagennummer:** Kunden gelten nur bei gleichem Namen und Ort (ohne Gross-/Kleinschreibung und Randleerzeichen) als gleich; Varianten wie „Lonza AG“ / „Lonza“ werden nicht erkannt.
- **Deaktivieren eines Technikers** storniert seine geplanten Reservationen nicht; er erhält nur keine neuen.
- **Lock-Timeout:** Für die Datensatzsperren ist kein Timeout konfiguriert; eine hängende Transaktion würde wartende Anfragen blockieren.
- **Sperrtest auf Repository-Ebene** wurde bewusst weggelassen; die Nebenläufigkeit ist über die API getestet (Doppelreservation).
- **Auswertungen 3–5** aus dem Steckbrief (freie Anlagen, Problemanlagen, Stornoquote) sind noch nicht umgesetzt.

## KI und Hilfsmittel

Siehe [`docs/ki/`](docs/ki/).
