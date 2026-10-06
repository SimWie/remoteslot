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
        long kundeId = kundeAnlegen("Lonza AG", "Visp");
        anlageAnlegen(kundeId, "ANL-0815", false);

        anlageAnlegen(kundeId, "ANL-0815", true);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM anlage WHERE anlagennummer = 'ANL-0815'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("V2: Zwei aktive Anlagen dürfen nicht dieselbe Anlagennummer haben")
    void zweiAktiveAnlagenMitGleicherNummerWerdenAbgelehnt() {
        long kundeId = kundeAnlegen("Lonza AG", "Visp");
        anlageAnlegen(kundeId, "ANL-0815", true);

        assertThatThrownBy(() -> anlageAnlegen(kundeId, "ANL-0815", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V3: Zwei Kunden mit gleichem Namen und Ort werden abgelehnt")
    void zweiKundenMitGleichemNamenUndOrtWerdenAbgelehnt() {
        kundeAnlegen("Lonza AG", "Visp");

        assertThatThrownBy(() -> kundeAnlegen("Lonza AG", "Visp"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V3: Gross-/Kleinschreibung und Leerzeichen am Rand gelten nicht als anderer Kunde")
    void kundeMitAndererSchreibweiseWirdAbgelehnt() {
        kundeAnlegen("Lonza AG", "Visp");

        assertThatThrownBy(() -> kundeAnlegen(" lonza ag ", "VISP"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V3: Derselbe Name an einem anderen Ort ist ein anderer Kunde")
    void gleicherNameAnAnderemOrtIstErlaubt() {
        kundeAnlegen("Lonza AG", "Visp");

        kundeAnlegen("Lonza AG", "Basel");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM kunde WHERE name = 'Lonza AG'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("V4: Zwei überlappende Reservationen auf derselben Anlage werden abgelehnt")
    void ueberlappendeReservationenAufDerselbenAnlageWerdenAbgelehnt() {
        long anlageId = anlageAnlegen();
        reservationEinfuegen(anlageId, technikerAnlegen("AAA"), "09:00", "11:00", "GEPLANT");

        assertThatThrownBy(() -> reservationEinfuegen(anlageId, technikerAnlegen("BBB"), "10:00", "12:00", "GEPLANT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_reservation_anlage_ueberschneidung");
    }

    @Test
    @DisplayName("V4: Derselbe Techniker kann nicht gleichzeitig auf zwei Anlagen eingeplant werden")
    void technikerKannNichtDoppeltEingeplantWerden() {
        long kundeId = kundeAnlegen("Lonza AG", "Visp");
        long technikerId = technikerAnlegen("AAA");
        reservationEinfuegen(anlageAnlegen(kundeId, "ANL-0001", true), technikerId, "09:00", "11:00", "GEPLANT");

        assertThatThrownBy(() -> reservationEinfuegen(anlageAnlegen(kundeId, "ANL-0002", true), technikerId,
                "10:30", "11:30", "GEPLANT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_reservation_techniker_ueberschneidung");
    }

    @Test
    @DisplayName("V4: Direkt aneinandergrenzende Fenster (Ende = Beginn) sind erlaubt")
    void aneinandergrenzendeFensterSindErlaubt() {
        long anlageId = anlageAnlegen();
        long technikerId = technikerAnlegen("AAA");
        reservationEinfuegen(anlageId, technikerId, "09:00", "10:00", "GEPLANT");

        reservationEinfuegen(anlageId, technikerId, "10:00", "11:00", "GEPLANT");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("V4: Eine stornierte Reservation blockiert den Zeitraum nicht")
    void stornierteReservationBlockiertNicht() {
        long anlageId = anlageAnlegen();
        long technikerId = technikerAnlegen("AAA");
        reservationEinfuegen(anlageId, technikerId, "09:00", "11:00", "STORNIERT");

        reservationEinfuegen(anlageId, technikerId, "09:00", "11:00", "GEPLANT");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("V5: Ein Verlaufseintrag GEPLANT -> GEPLANT (Verschieben) braucht eine Bemerkung")
    void verlaufseintragOhneStatuswechselBrauchtBemerkung() {
        reservationEinfuegen(anlageAnlegen(), technikerAnlegen("AAA"), "09:00", "10:00", "GEPLANT");
        long reservationId = Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM reservation", Long.class));

        jdbc.update("""
                INSERT INTO statusereignis (reservation_id, alter_status, neuer_status, bemerkung)
                VALUES (?, 'GEPLANT', 'GEPLANT', 'Verschoben von ... auf ...')
                """, reservationId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO statusereignis (reservation_id, alter_status, neuer_status)
                VALUES (?, 'GEPLANT', 'GEPLANT')
                """, reservationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V5: Andere Einträge ohne Statuswechsel bleiben verboten, auch mit Bemerkung")
    void andereEintraegeOhneStatuswechselBleibenVerboten() {
        reservationEinfuegen(anlageAnlegen(), technikerAnlegen("AAA"), "09:00", "10:00", "AKTIV");
        long reservationId = Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM reservation", Long.class));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO statusereignis (reservation_id, alter_status, neuer_status, bemerkung)
                VALUES (?, 'AKTIV', 'AKTIV', 'irgendwas')
                """, reservationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long anlageAnlegen() {
        return anlageAnlegen(kundeAnlegen("Lonza AG", "Visp"), "ANL-0001", true);
    }

    private long kundeAnlegen(String name, String ort) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES (?, ?) RETURNING id", Long.class, name, ort));
    }

    private long anlageAnlegen(long kundeId, String anlagennummer, boolean aktiv) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp, aktiv)
                VALUES (?, ?, 'Palettierer Halle 2', 'S7-1500', ?) RETURNING id
                """, Long.class, kundeId, anlagennummer, aktiv));
    }

    private long technikerAnlegen(String kuerzel) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO techniker (kuerzel, vorname, nachname) VALUES (?, 'Test', 'Person') RETURNING id",
                Long.class, kuerzel));
    }

    /** Reservation direkt per SQL am 01.10.2026 (Schweizer Zeit), ohne API und ohne Anwendungsregeln. */
    private void reservationEinfuegen(long anlageId, long technikerId, String von, String bis, String status) {
        jdbc.update("""
                INSERT INTO reservation (anlage_id, techniker_id, beginn, ende, zweck, status)
                VALUES (?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), 'UPDATE', ?)
                """, anlageId, technikerId, "2026-10-01 " + von + "+02", "2026-10-01 " + bis + "+02", status);
    }
}
