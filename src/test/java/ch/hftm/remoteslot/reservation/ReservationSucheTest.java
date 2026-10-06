package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A6/T7: Suche mit optionalen Filtern, stabiler Sortierung und Pagination.
 *
 * Testdaten (Schweizer Zeit, November 2026):
 *   r1  02.11. 09-10  Anlage 1 (Kunde 1)  Techniker 1  GEPLANT
 *   r2  02.11. 09-10  Anlage 3 (Kunde 2)  Techniker 2  GEPLANT        gleicher Beginn wie r1 -> Reihenfolge nach ID
 *   r3  03.11. 09-11  Anlage 2 (Kunde 1)  Techniker 1  ABGESCHLOSSEN
 *   r4  04.11. 13-14  Anlage 1 (Kunde 1)  Techniker 2  STORNIERT
 *   r5  05.11. 08-09  Anlage 3 (Kunde 2)  Techniker 1  AKTIV
 */
@SuppressWarnings("null")
class ReservationSucheTest extends AbstractIntegrationTest {

    private long kunde1, kunde2, anlage1, anlage2, anlage3, techniker1, techniker2;
    private int r1, r2, r3, r4, r5;

    @BeforeEach
    void setUp() {
        tabellenLeeren();
        kunde1 = id("INSERT INTO kunde (name, ort) VALUES ('Lonza AG', 'Visp') RETURNING id");
        kunde2 = id("INSERT INTO kunde (name, ort) VALUES ('Novartis AG', 'Basel') RETURNING id");
        anlage1 = anlage(kunde1, "ANL-0001");
        anlage2 = anlage(kunde1, "ANL-0002");
        anlage3 = anlage(kunde2, "ANL-0003");
        techniker1 = id("INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('AMU', 'Anna', 'Muster') RETURNING id");
        techniker2 = id("INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('BXY', 'Beat', 'Beispiel') RETURNING id");

        r1 = reservation(anlage1, techniker1, "2026-11-02 09:00", "2026-11-02 10:00", "GEPLANT");
        r2 = reservation(anlage3, techniker2, "2026-11-02 09:00", "2026-11-02 10:00", "GEPLANT");
        r3 = reservation(anlage2, techniker1, "2026-11-03 09:00", "2026-11-03 11:00", "ABGESCHLOSSEN");
        r4 = reservation(anlage1, techniker2, "2026-11-04 13:00", "2026-11-04 14:00", "STORNIERT");
        r5 = reservation(anlage3, techniker1, "2026-11-05 08:00", "2026-11-05 09:00", "AKTIV");
    }

    @Test
    @DisplayName("Ohne Filter: alle Reservationen, sortiert nach Beginn und bei gleichem Beginn nach ID")
    void ohneFilterAlleSortiert() throws Exception {
        mockMvc.perform(get("/api/reservationen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inhalt[*].id", contains(r1, r2, r3, r4, r5)))
                .andExpect(jsonPath("$.gesamtanzahl").value(5));
    }

    @Test
    @DisplayName("Filter Kunde: nur Reservationen auf Anlagen dieses Kunden")
    void filterKunde() throws Exception {
        mockMvc.perform(get("/api/reservationen").param("kundeId", String.valueOf(kunde1)))
                .andExpect(jsonPath("$.inhalt[*].id", contains(r1, r3, r4)))
                .andExpect(jsonPath("$.gesamtanzahl").value(3));
    }

    @Test
    @DisplayName("Filter Anlage")
    void filterAnlage() throws Exception {
        mockMvc.perform(get("/api/reservationen").param("anlageId", String.valueOf(anlage3)))
                .andExpect(jsonPath("$.inhalt[*].id", contains(r2, r5)));
    }

    @Test
    @DisplayName("Filter Techniker und Status kombiniert")
    void filterTechnikerUndStatus() throws Exception {
        mockMvc.perform(get("/api/reservationen")
                        .param("technikerId", String.valueOf(techniker1))
                        .param("status", "GEPLANT"))
                .andExpect(jsonPath("$.inhalt[*].id", contains(r1)))
                .andExpect(jsonPath("$.gesamtanzahl").value(1));
    }

    @Test
    @DisplayName("Filter Zeitraum: liefert, was sich mit [von, bis) überschneidet; Fenster, die genau an den Grenzen enden bzw. beginnen, nicht")
    void filterZeitraumMitGrenzen() throws Exception {
        // r1/r2 enden genau um 10:00 (= von), r4 beginnt genau um 13:00 (= bis) -> beide nicht enthalten
        mockMvc.perform(get("/api/reservationen")
                        .param("von", "2026-11-02T10:00:00+01:00")
                        .param("bis", "2026-11-04T13:00:00+01:00"))
                .andExpect(jsonPath("$.inhalt[*].id", contains(r3)));
    }

    @Test
    @DisplayName("Filter Kunde und Zeitraum kombiniert")
    void filterKundeUndZeitraum() throws Exception {
        mockMvc.perform(get("/api/reservationen")
                        .param("kundeId", String.valueOf(kunde1))
                        .param("von", "2026-11-03T00:00:00+01:00"))
                .andExpect(jsonPath("$.inhalt[*].id", contains(r3, r4)));
    }

    @Test
    @DisplayName("Pagination: Seiten mit Gesamtanzahl, Reihenfolge über alle Seiten stabil")
    void paginationStabil() throws Exception {
        mockMvc.perform(get("/api/reservationen").param("seite", "0").param("groesse", "2"))
                .andExpect(jsonPath("$.inhalt[*].id", contains(r1, r2)))
                .andExpect(jsonPath("$.gesamtanzahl").value(5))
                .andExpect(jsonPath("$.seitenanzahl").value(3));
        mockMvc.perform(get("/api/reservationen").param("seite", "1").param("groesse", "2"))
                .andExpect(jsonPath("$.inhalt[*].id", contains(r3, r4)));
        mockMvc.perform(get("/api/reservationen").param("seite", "2").param("groesse", "2"))
                .andExpect(jsonPath("$.inhalt[*].id", contains(r5)));
        mockMvc.perform(get("/api/reservationen").param("seite", "3").param("groesse", "2"))
                .andExpect(jsonPath("$.inhalt", empty()))
                .andExpect(jsonPath("$.gesamtanzahl").value(5));
    }

    @Test
    @DisplayName("Ungültige Seitengrösse (0 oder über 100) wird mit 400 abgelehnt")
    void ungueltigeSeitengroesse() throws Exception {
        mockMvc.perform(get("/api/reservationen").param("groesse", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reservationen").param("groesse", "101")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ein unbekannter Status im Filter wird mit 400 abgelehnt")
    void unbekannterStatus() throws Exception {
        mockMvc.perform(get("/api/reservationen").param("status", "ERLEDIGT"))
                .andExpect(status().isBadRequest());
    }

    // --- Hilfsmethoden ---------------------------------------------------------------------------

    private long id(String sql) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class));
    }

    private long anlage(long kundeId, String nummer) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp)
                VALUES (?, ?, 'Palettierer', 'S7-1500') RETURNING id
                """, Long.class, kundeId, nummer));
    }

    /** Direkt per SQL (Zeiten in Schweizer Winterzeit), damit beliebige Status und Daten moeglich sind. */
    private int reservation(long anlageId, long technikerId, String von, String bis, String status) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO reservation (anlage_id, techniker_id, beginn, ende, zweck, status)
                VALUES (?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), 'WARTUNG', ?) RETURNING id
                """, Integer.class, anlageId, technikerId, von + "+01", bis + "+01", status));
    }
}
