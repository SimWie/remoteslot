package ch.hftm.remoteslot.anlage;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T4: "Anlage ausser Betrieb nehmen" als atomare Operation (Steckbrief Abschnitt 4).
 * Deaktivieren, Stornieren und Verlaufseintraege gelingen gemeinsam oder werden gemeinsam zurueckgenommen.
 */
@SuppressWarnings("null")
class AnlageAusserBetriebTest extends AbstractIntegrationTest {

    private final Instant jetzt = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private long kundeId;
    private long anlageId;
    private long technikerId;

    @BeforeEach
    void setUp() {
        tabellenLeeren();
        kundeId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES ('Lonza AG', 'Visp') RETURNING id", Long.class));
        anlageId = anlageAnlegen("ANL-0815");
        technikerId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('AMU', 'Anna', 'Muster') RETURNING id", Long.class));
    }

    @Test
    @DisplayName("Ausser Betrieb nehmen storniert alle GEPLANT-Reservationen (auch überfällige) und schreibt den Verlauf")
    void ausserBetriebNehmenStorniertGeplanteReservationen() throws Exception {
        long ueberfaellig = reservation(anlageId, tage(-1), "GEPLANT");
        long kuenftig = reservation(anlageId, tage(7), "GEPLANT");
        long abgeschlossen = reservation(anlageId, tage(-3), "ABGESCHLOSSEN");
        long storniert = reservation(anlageId, tage(-2), "STORNIERT");

        mockMvc.perform(post("/api/anlagen/{id}/ausser-betrieb", anlageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiv").value(false));

        assertThat(statusVon(ueberfaellig)).isEqualTo("STORNIERT");
        assertThat(statusVon(kuenftig)).isEqualTo("STORNIERT");
        assertThat(statusVon(abgeschlossen)).isEqualTo("ABGESCHLOSSEN");   // abgeschlossene bleiben fuer Auswertungen
        assertThat(statusVon(storniert)).isEqualTo("STORNIERT");
        assertThat(jdbc.queryForList("""
                SELECT bemerkung FROM statusereignis
                WHERE alter_status = 'GEPLANT' AND neuer_status = 'STORNIERT'
                """, String.class))
                .hasSize(2)
                .containsOnly("Anlage ausser Betrieb genommen");
    }

    @Test
    @DisplayName("Rollback: Bei einer laufenden Reservation wird alles zurückgenommen, auch bereits geschriebene Änderungen")
    void beiLaufenderReservationWirdAllesZurueckgenommen() throws Exception {
        // Reihenfolge nach Beginn: zuerst wird die ueberfaellige GEPLANT-Reservation storniert und geschrieben,
        // danach trifft die Operation auf die laufende (AKTIV) Reservation und bricht ab.
        long ueberfaellig = reservation(anlageId, tage(-1), "GEPLANT");
        long laufend = reservation(anlageId, jetzt.minus(Duration.ofMinutes(30)), "AKTIV");
        long kuenftig = reservation(anlageId, tage(7), "GEPLANT");

        mockMvc.perform(post("/api/anlagen/{id}/ausser-betrieb", anlageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Anlage ANL-0815 hat eine laufende Reservation (" + laufend
                        + ") und kann nicht ausser Betrieb genommen werden."));

        assertThat(jdbc.queryForObject("SELECT aktiv FROM anlage WHERE id = ?", Boolean.class, anlageId)).isTrue();
        assertThat(statusVon(ueberfaellig)).isEqualTo("GEPLANT");
        assertThat(statusVon(laufend)).isEqualTo("AKTIV");
        assertThat(statusVon(kuenftig)).isEqualTo("GEPLANT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM statusereignis", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM reservation WHERE id = ?", Long.class, ueberfaellig))
                .isZero();
    }

    @Test
    @DisplayName("Reservationen anderer Anlagen bleiben unverändert")
    void reservationenAndererAnlagenBleibenUnveraendert() throws Exception {
        long andereAnlage = anlageAnlegen("ANL-0816");
        long fremd = reservation(andereAnlage, tage(7), "GEPLANT");
        reservation(anlageId, tage(8), "GEPLANT");

        mockMvc.perform(post("/api/anlagen/{id}/ausser-betrieb", anlageId))
                .andExpect(status().isOk());

        assertThat(statusVon(fremd)).isEqualTo("GEPLANT");
    }

    private Instant tage(int tage) {
        return jetzt.plus(Duration.ofDays(tage));
    }

    private String statusVon(long reservationId) {
        return jdbc.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId);
    }

    private long anlageAnlegen(String anlagennummer) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp)
                VALUES (?, ?, 'Palettierer Halle 2', 'S7-1500') RETURNING id
                """, Long.class, kundeId, anlagennummer));
    }

    /** Reservation von einer Stunde, direkt per SQL (auch in der Vergangenheit und mit beliebigem Status). */
    private long reservation(long anlageId, Instant beginn, String status) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO reservation (anlage_id, techniker_id, beginn, ende, zweck, status)
                VALUES (?, ?, ?, ?, 'WARTUNG', ?) RETURNING id
                """, Long.class, anlageId, technikerId, beginn.atOffset(ZoneOffset.UTC),
                beginn.plus(Duration.ofHours(1)).atOffset(ZoneOffset.UTC), status));
    }
}
