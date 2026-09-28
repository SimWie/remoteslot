package ch.hftm.remoteslot;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;

/** T1/T2: Aufbau auf leerer Datenbank und datenbankseitige Regeln unabhaengig von der API. */
class SchemaMigrationTest extends AbstractIntegrationTest {

    @Autowired
    Flyway flyway;

    @BeforeEach
    void setUp() {
        tabellenLeeren();
    }

    @Test
    @DisplayName("Flyway hat alle Migrationen angewendet und alle Tabellen existieren")
    void alleMigrationenSindAufLeererDatenbankAngewendet() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).isNotEmpty();

        var tabellen = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, String.class);

        assertThat(tabellen).contains("kunde", "anlage", "techniker", "reservation", "statusereignis");
    }

    @Test
    @DisplayName("Die Datenbank lehnt eine Reservation mit Ende vor Beginn ab")
    void datenbankLehntReservationMitEndeVorBeginnAb() {
        long anlageId = anlageAnlegen();
        long technikerId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('TST', 'Test', 'Person') RETURNING id", Long.class));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO reservation (anlage_id, techniker_id, beginn, ende, zweck)
                VALUES (?, ?, TIMESTAMPTZ '2026-10-01 10:00+02', TIMESTAMPTZ '2026-10-01 09:00+02', 'UPDATE')
                """, anlageId, technikerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Die Datenbank lehnt einen unbekannten Status ab")
    void datenbankLehntUnbekanntenStatusAb() {
        long anlageId = anlageAnlegen();
        long technikerId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('TST', 'Test', 'Person') RETURNING id", Long.class));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO reservation (anlage_id, techniker_id, beginn, ende, zweck, status)
                VALUES (?, ?, TIMESTAMPTZ '2026-10-01 09:00+02', TIMESTAMPTZ '2026-10-01 10:00+02', 'UPDATE', 'ERLEDIGT')
                """, anlageId, technikerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long anlageAnlegen() {
        long kundeId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES ('Muster AG', 'Bern') RETURNING id", Long.class));
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp)
                VALUES (?, 'ANL-0001', 'Palettierer Halle 2', 'S7-1500') RETURNING id
                """, Long.class, kundeId));
    }
}
