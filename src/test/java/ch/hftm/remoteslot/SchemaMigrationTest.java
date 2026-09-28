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

    @Test
    @DisplayName("V2: Eine inaktive und eine aktive Anlage dürfen dieselbe Anlagennummer haben")
    void inaktiveUndAktiveAnlageMitGleicherNummerSindErlaubt() {
        long kundeId = kundeAnlegen();
        anlageAnlegen(kundeId, "ANL-0815", false);

        anlageAnlegen(kundeId, "ANL-0815", true);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM anlage WHERE anlagennummer = 'ANL-0815'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("V2: Zwei aktive Anlagen dürfen nicht dieselbe Anlagennummer haben")
    void zweiAktiveAnlagenMitGleicherNummerWerdenAbgelehnt() {
        long kundeId = kundeAnlegen();
        anlageAnlegen(kundeId, "ANL-0815", true);

        assertThatThrownBy(() -> anlageAnlegen(kundeId, "ANL-0815", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long anlageAnlegen() {
        return anlageAnlegen(kundeAnlegen(), "ANL-0001", true);
    }

    private long kundeAnlegen() {
        return Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES ('Muster AG', 'Bern') RETURNING id", Long.class));
    }

    private long anlageAnlegen(long kundeId, String anlagennummer, boolean aktiv) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp, aktiv)
                VALUES (?, ?, 'Palettierer Halle 2', 'S7-1500', ?) RETURNING id
                """, Long.class, kundeId, anlagennummer, aktiv));
    }
}
