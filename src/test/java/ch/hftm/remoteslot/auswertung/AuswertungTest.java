package ch.hftm.remoteslot.auswertung;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A7/T6: Auswertungen ueber die View v_einsatz mit JDBC.
 *
 * Testdaten (Schweizer Zeit):
 *   Lonza (Anlagen 1 und 2), Novartis (Anlage 3); Techniker AMU und BXY
 *   05.10. 09:00-11:00  Anlage 1  AMU  UPDATE     ABGESCHLOSSEN   2.0 h
 *   12.10. 09:00-10:30  Anlage 2  AMU  STOERUNG   ABGESCHLOSSEN   1.5 h
 *   03.11. 09:00-10:00  Anlage 1  BXY  STOERUNG   ABGESCHLOSSEN   1.0 h
 *   01.11. 00:30-01:30  Anlage 3  AMU  WARTUNG    ABGESCHLOSSEN   1.0 h   in UTC noch 31.10. -> zaehlt zum November
 *   20.10. 09:00-10:00  Anlage 3  BXY  UPDATE     STORNIERT               zaehlt nicht
 *   21.10. 09:00-10:00  Anlage 3  BXY  UPDATE     GEPLANT                 zaehlt nicht
 */
@SuppressWarnings("null")
class AuswertungTest extends AbstractIntegrationTest {

    private long technikerAmu;
    private long technikerBxy;

    @BeforeEach
    void setUp() {
        tabellenLeeren();
        long lonza = id("INSERT INTO kunde (name, ort) VALUES ('Lonza AG', 'Visp') RETURNING id");
        long novartis = id("INSERT INTO kunde (name, ort) VALUES ('Novartis AG', 'Basel') RETURNING id");
        long anlage1 = anlage(lonza, "ANL-0001");
        long anlage2 = anlage(lonza, "ANL-0002");
        long anlage3 = anlage(novartis, "ANL-0003");
        technikerAmu = id("INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('AMU', 'Anna', 'Muster') RETURNING id");
        technikerBxy = id("INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('BXY', 'Beat', 'Beispiel') RETURNING id");

        long r1 = reservation(anlage1, technikerAmu, "2026-10-05 09:00+02", "2026-10-05 11:00+02", "UPDATE", "ABGESCHLOSSEN");
        reservation(anlage2, technikerAmu, "2026-10-12 09:00+02", "2026-10-12 10:30+02", "STOERUNG", "ABGESCHLOSSEN");
        reservation(anlage1, technikerBxy, "2026-11-03 09:00+01", "2026-11-03 10:00+01", "STOERUNG", "ABGESCHLOSSEN");
        reservation(anlage3, technikerAmu, "2026-11-01 00:30+01", "2026-11-01 01:30+01", "WARTUNG", "ABGESCHLOSSEN");
        reservation(anlage3, technikerBxy, "2026-10-20 09:00+02", "2026-10-20 10:00+02", "UPDATE", "STORNIERT");
        reservation(anlage3, technikerBxy, "2026-10-21 09:00+02", "2026-10-21 10:00+02", "UPDATE", "GEPLANT");

        // Mehrere Verlaufseintraege fuer r1: duerfen die Zaehlung nicht beeinflussen (keine Doppelzaehlung)
        for (String[] wechsel : new String[][]{{null, "GEPLANT"}, {"GEPLANT", "AKTIV"}, {"AKTIV", "ABGESCHLOSSEN"}}) {
            jdbc.update("INSERT INTO statusereignis (reservation_id, alter_status, neuer_status) VALUES (?, ?, ?)",
                    r1, wechsel[0], wechsel[1]);
        }
    }

    @Test
    @DisplayName("Aufwand je Kunde und Monat: nur ABGESCHLOSSEN, Monat in Schweizer Zeit, keine Doppelzählung")
    void aufwandJeKundeUndMonat() throws Exception {
        mockMvc.perform(get("/api/auswertungen/aufwand-kunde").param("von", "2026-10-01").param("bis", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Lonza Oktober: 2 Einsaetze auf zwei verschiedenen Anlagen, 2.0 + 1.5 h
                .andExpect(jsonPath("$[0].kunde").value("Lonza AG"))
                .andExpect(jsonPath("$[0].monat").value("2026-10"))
                .andExpect(jsonPath("$[0].einsaetze").value(2))
                .andExpect(jsonPath("$[0].stunden").value(3.5))
                .andExpect(jsonPath("$[1].kunde").value("Lonza AG"))
                .andExpect(jsonPath("$[1].monat").value("2026-11"))
                .andExpect(jsonPath("$[1].einsaetze").value(1))
                // Novartis: der Einsatz am 01.11. 00:30 Schweizer Zeit gehoert zum November
                .andExpect(jsonPath("$[2].kunde").value("Novartis AG"))
                .andExpect(jsonPath("$[2].monat").value("2026-11"))
                .andExpect(jsonPath("$[2].einsaetze").value(1))
                .andExpect(jsonPath("$[2].stunden").value(1.0));
    }

    @Test
    @DisplayName("Zeitraum in Schweizer Tagen: ab 01.11. zählt der Einsatz um 00:30 mit, der Oktober nicht")
    void zeitraumGrenzeInSchweizerZeit() throws Exception {
        mockMvc.perform(get("/api/auswertungen/aufwand-kunde").param("von", "2026-11-01").param("bis", "2026-12-01"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].kunde").value("Lonza AG"))
                .andExpect(jsonPath("$[0].monat").value("2026-11"))
                .andExpect(jsonPath("$[1].kunde").value("Novartis AG"))
                .andExpect(jsonPath("$[1].einsaetze").value(1));
    }

    @Test
    @DisplayName("Aufwand je Techniker, aufgeteilt nach Zweck")
    void aufwandJeTechnikerUndZweck() throws Exception {
        mockMvc.perform(get("/api/auswertungen/aufwand-techniker").param("von", "2026-10-01").param("bis", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].kuerzel").value("AMU"))
                .andExpect(jsonPath("$[0].zweck").value("STOERUNG"))
                .andExpect(jsonPath("$[0].stunden").value(1.5))
                .andExpect(jsonPath("$[1].zweck").value("UPDATE"))
                .andExpect(jsonPath("$[1].stunden").value(2.0))
                .andExpect(jsonPath("$[2].zweck").value("WARTUNG"))
                .andExpect(jsonPath("$[3].kuerzel").value("BXY"))
                .andExpect(jsonPath("$[3].zweck").value("STOERUNG"))
                .andExpect(jsonPath("$[3].einsaetze").value(1));
    }

    @Test
    @DisplayName("Ein Zeitraum ohne Einsätze liefert eine leere Liste")
    void leererZeitraum() throws Exception {
        mockMvc.perform(get("/api/auswertungen/aufwand-kunde").param("von", "2025-01-01").param("bis", "2025-02-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Ein ungültiger Zeitraum (bis vor von) oder ein fehlender Parameter wird mit 400 abgelehnt")
    void ungueltigerZeitraum() throws Exception {
        mockMvc.perform(get("/api/auswertungen/aufwand-kunde").param("von", "2026-12-01").param("bis", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auswertungen/aufwand-techniker").param("von", "2026-10-01"))
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

    private long reservation(long anlageId, long technikerId, String von, String bis, String zweck, String status) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO reservation (anlage_id, techniker_id, beginn, ende, zweck, status)
                VALUES (?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), ?, ?) RETURNING id
                """, Long.class, anlageId, technikerId, von, bis, zweck, status));
    }
}
