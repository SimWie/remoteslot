package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A5: Status aendern, verschieben und stornieren, inkl. optimistischem Sperren ueber die API. */
@SuppressWarnings("null")
class ReservationAenderungTest extends AbstractIntegrationTest {

    private static final ZoneId ZUERICH = ZoneId.of("Europe/Zurich");

    private ZonedDateTime beginn;
    private long anlageId;
    private long technikerId;

    @BeforeEach
    void setUp() {
        tabellenLeeren();
        beginn = ZonedDateTime.now(ZUERICH).plusDays(7).truncatedTo(ChronoUnit.DAYS).withHour(9);
        long kundeId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES ('Lonza AG', 'Visp') RETURNING id", Long.class));
        anlageId = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp)
                VALUES (?, 'ANL-0815', 'Palettierer Halle 2', 'S7-1500') RETURNING id
                """, Long.class, kundeId));
        technikerId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('AMU', 'Anna', 'Muster') RETURNING id", Long.class));
    }

    @Test
    @DisplayName("Der normale Ablauf GEPLANT -> AKTIV -> ABGESCHLOSSEN erhöht die Version und schreibt den Verlauf")
    void normalerAblaufBisAbgeschlossen() throws Exception {
        long id = anlegen(beginn, beginn.plusHours(2));

        statusSetzen(id, "AKTIV", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AKTIV"))
                .andExpect(jsonPath("$.version").value(1));
        statusSetzen(id, "ABGESCHLOSSEN", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/reservationen/{id}/verlauf", id))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].alterStatus").value("GEPLANT"))
                .andExpect(jsonPath("$[1].neuerStatus").value("AKTIV"))
                .andExpect(jsonPath("$[2].neuerStatus").value("ABGESCHLOSSEN"));
    }

    @Test
    @DisplayName("Eine stornierte Reservation gibt den Zeitraum wieder frei")
    void stornierenGibtZeitraumFrei() throws Exception {
        long id = anlegen(beginn, beginn.plusHours(2));

        statusSetzen(id, "STORNIERT", 0).andExpect(status().isOk());

        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(anlegenBody(beginn, beginn.plusHours(2))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Ein unzulässiger Übergang (GEPLANT -> ABGESCHLOSSEN) wird mit 409 abgelehnt")
    void unzulaessigerUebergangWirdAbgelehnt() throws Exception {
        long id = anlegen(beginn, beginn.plusHours(2));

        statusSetzen(id, "ABGESCHLOSSEN", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Statuswechsel von GEPLANT zu ABGESCHLOSSEN ist nicht zulaessig."));

        assertThat(statusVon(id)).isEqualTo("GEPLANT");
    }

    @Test
    @DisplayName("Verlorene Änderung: Eine Änderung auf einem veralteten Stand wird abgelehnt statt überschrieben")
    void aenderungAufVeraltetemStandWirdAbgelehnt() throws Exception {
        long id = anlegen(beginn, beginn.plusHours(2));
        // Disponent A und B haben beide Version 0 gesehen. A verschiebt zuerst ...
        verschieben(id, beginn.plusHours(1), beginn.plusHours(3), 0).andExpect(status().isOk());

        // ... B storniert danach auf Basis des veralteten Stands
        statusSetzen(id, "STORNIERT", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Der Datensatz wurde inzwischen geaendert. Bitte neu laden und die Aenderung wiederholen."));

        assertThat(statusVon(id)).isEqualTo("GEPLANT");
    }

    @Test
    @DisplayName("Verschieben ändert den Zeitraum und protokolliert alten und neuen Zeitraum im Verlauf")
    void verschiebenWirdProtokolliert() throws Exception {
        long id = anlegen(beginn, beginn.plusHours(2));
        ZonedDateTime neu = beginn.plusDays(1);

        verschieben(id, neu, neu.plusHours(1), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beginn").value(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(neu.toOffsetDateTime())))
                .andExpect(jsonPath("$.status").value("GEPLANT"))
                .andExpect(jsonPath("$.version").value(1));

        String bemerkung = jdbc.queryForObject("""
                SELECT bemerkung FROM statusereignis
                WHERE reservation_id = ? AND alter_status = 'GEPLANT' AND neuer_status = 'GEPLANT'
                """, String.class, id);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        assertThat(bemerkung).isEqualTo("Verschoben von " + f.format(beginn) + " bis " + f.format(beginn.plusHours(2))
                + " auf " + f.format(neu) + " bis " + f.format(neu.plusHours(1)));
    }

    @Test
    @DisplayName("Eine leicht verschobene Reservation kollidiert nicht mit sich selbst")
    void verschiebenUeberlapptNichtMitSichSelbst() throws Exception {
        long id = anlegen(beginn, beginn.plusHours(2));

        verschieben(id, beginn.plusMinutes(30), beginn.plusMinutes(150), 0).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Verschieben auf einen belegten Zeitraum wird mit 409 abgelehnt")
    void verschiebenAufBelegtenZeitraumWirdAbgelehnt() throws Exception {
        anlegen(beginn, beginn.plusHours(2));
        long spaeter = anlegen(beginn.plusHours(4), beginn.plusHours(5));

        verschieben(spaeter, beginn.plusHours(1), beginn.plusHours(2), 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Anlage ANL-0815 ist im gewuenschten Zeitraum bereits reserviert."));
    }

    @Test
    @DisplayName("Eine laufende (AKTIV) Reservation kann nicht verschoben werden")
    void aktiveReservationKannNichtVerschobenWerden() throws Exception {
        long id = anlegen(beginn, beginn.plusHours(2));
        statusSetzen(id, "AKTIV", 0).andExpect(status().isOk());

        verschieben(id, beginn.plusDays(1), beginn.plusDays(1).plusHours(1), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Nur geplante Reservationen koennen verschoben werden (Status: AKTIV)."));
    }

    @Test
    @DisplayName("Beim Verschieben gelten dieselben Zeitregeln wie beim Anlegen (400)")
    void verschiebenPrueftDauer() throws Exception {
        long id = anlegen(beginn, beginn.plusHours(2));

        verschieben(id, beginn, beginn.plusMinutes(5), 0).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Statusänderung einer unbekannten Reservation liefert 404")
    void unbekannteReservationLiefert404() throws Exception {
        statusSetzen(999, "AKTIV", 0).andExpect(status().isNotFound());
    }

    // --- Hilfsmethoden ---------------------------------------------------------------------------

    private long anlegen(ZonedDateTime von, ZonedDateTime bis) throws Exception {
        String antwort = mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON).content(anlegenBody(von, bis)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(antwort, "$.id")).longValue();
    }

    private ResultActions statusSetzen(long id, String neuerStatus, long version) throws Exception {
        return mockMvc.perform(post("/api/reservationen/{id}/status", id).contentType(APPLICATION_JSON).content("""
                {
                  "neuerStatus": "%s",
                  "version":     %d
                }
                """.formatted(neuerStatus, version)));
    }

    private ResultActions verschieben(long id, ZonedDateTime von, ZonedDateTime bis,
                                                                           long version) throws Exception {
        return mockMvc.perform(put("/api/reservationen/{id}/zeitraum", id).contentType(APPLICATION_JSON).content("""
                {
                  "beginn":  "%s",
                  "ende":    "%s",
                  "version": %d
                }
                """.formatted(von.toInstant(), bis.toInstant(), version)));
    }

    private String anlegenBody(ZonedDateTime von, ZonedDateTime bis) {
        return """
                {
                  "anlageId":    %d,
                  "technikerId": %d,
                  "beginn":      "%s",
                  "ende":        "%s",
                  "zweck":       "WARTUNG"
                }
                """.formatted(anlageId, technikerId, von.toInstant(), bis.toInstant());
    }

    private String statusVon(long id) {
        return jdbc.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, id);
    }
}
