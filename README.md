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

*Folgt:* `psql -h localhost -U remoteslot -d remoteslot -f scripts/testdaten.sql`

## ER-Diagramm

Siehe [`docs/er-diagramm.md`](docs/er-diagramm.md).

## Nachweise

| Anforderung | Umsetzung | Nachweis |
|---|---|---|
| A1 Kunden und Anlagen verwalten | – | – |
| A2 Techniker verwalten | `techniker/` | `TechnikerApiTest`, `http/techniker.http` |
| A3 Fernwartungsfenster reservieren | – | – |
| A4 Überschneidungen verhindern | – | – |
| A5 Status ändern, verschieben, stornieren | – | – |
| A6 Reservationen suchen | – | – |
| A7 Fernwartungsaufwand auswerten | – | – |
| T1 Datenmodell und Integrität | `V1__grundschema.sql`, `docs/er-diagramm.md` | `SchemaMigrationTest`, `TechnikerApiTest#datenbankErzwingtEindeutigkeitAuchOhneApi` |
| T2 Migrationen | `db/migration/` | `SchemaMigrationTest` *(Upgrade-Test V1 → neueste Version folgt)* |
| T3 Anwendung und JPA | – | – |
| T4 Transaktion und Rollback | – | – |
| T5 Gleichzeitige Änderungen | – | – |
| T6 Query Design, JDBC, View | – | – |
| T7 Filter, Sortierung, Pagination | – | – |
| T8 Integrationstests | `src/test/` | `./mvnw test` |
| T9 Testdaten | – | – |
| T10 Performance und Index | – | `docs/performance/` |
| T11 ORM-Ladeverhalten | Profil `sqllog` | – |
| T12 Cache und Aktualität | – | – |

## Einschränkungen

*Ehrlich festhalten, was nicht umgesetzt ist.*

## KI und Hilfsmittel

Siehe [`docs/ki/`](docs/ki/).
