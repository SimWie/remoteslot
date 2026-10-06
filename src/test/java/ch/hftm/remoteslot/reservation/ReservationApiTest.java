package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A3: Fernwartungsfenster reservieren. Gueltige und ungueltige Eingaben gegen echtes PostgreSQL. */
@SuppressWarnings("null")
class ReservationApiTest extends AbstractIntegrationTest {

    private static final ZoneId ZUERICH = ZoneId.of("Europe/Zurich");

    /** Immer in der Zukunft, damit die Tests nicht mit der Zeit veralten: in einer Woche, 09:00 Schweizer Zeit. */
    private ZonedDateTime beginn;
    private long kundeId;
    private long anlageId;
    private long technikerId;

    @BeforeEach
    void setUp() {
        tabellenLeeren();
        beginn = ZonedDateTime.now(ZUERICH).plusDays(7).truncatedTo(ChronoUnit.DAYS).withHour(9);
        kundeId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES ('Lonza AG', 'Visp') RETURNING id", Long.class));
        anlageId = anlageAnlegen("ANL-0815", true);
        technikerId = technikerAnlegen("AMU", true);
    }

    @Test
    @DisplayName("Eine Reservation wird als GEPLANT angelegt und in Schweizer Zeit zurückgegeben")
    void reservationWirdAngelegt() throws Exception {
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, beginn.toInstant(), beginn.plusHours(2).toInstant())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("GEPLANT"))
                .andExpect(jsonPath("$.zweck").value("UPDATE"))
                .andExpect(jsonPath("$.version").value(0))
                // Eingabe in UTC, Ausgabe in Schweizer Zeit mit passendem Offset
                .andExpect(jsonPath("$.beginn").value(iso(beginn)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Reservation und erster Verlaufseintrag werden gemeinsam geschrieben")
    void anlegenSchreibtErstenVerlaufseintrag() throws Exception {
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, beginn.toInstant(), beginn.plusHours(2).toInstant())))
                .andExpect(status().isCreated());
        long id = Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM reservation", Long.class));

        mockMvc.perform(get("/api/reservationen/{id}/verlauf", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].alterStatus").isEmpty())
                .andExpect(jsonPath("$[0].neuerStatus").value("GEPLANT"));
    }

    @Test
    @DisplayName("Eine ausser Betrieb genommene Anlage kann nicht reserviert werden (409)")
    void inaktiveAnlageWirdMit409Abgelehnt() throws Exception {
        long inaktiveAnlage = anlageAnlegen("ANL-0999", false);

        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(inaktiveAnlage, technikerId, beginn.toInstant(), beginn.plusHours(2).toInstant())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Anlage ANL-0999 ist ausser Betrieb und kann nicht reserviert werden."));

        assertKeineReservation();
    }

    @Test
    @DisplayName("Ein deaktivierter Techniker kann keine Reservation erhalten (409)")
    void deaktivierterTechnikerWirdMit409Abgelehnt() throws Exception {
        long deaktiviert = technikerAnlegen("BXY", false);

        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, deaktiviert, beginn.toInstant(), beginn.plusHours(2).toInstant())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Techniker BXY ist deaktiviert und kann nicht reserviert werden."));

        assertKeineReservation();
    }

    @Test
    @DisplayName("Eine unbekannte Anlage liefert 404")
    void unbekannteAnlageLiefert404() throws Exception {
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(999, technikerId, beginn.toInstant(), beginn.plusHours(2).toInstant())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Anlage 999 nicht gefunden."));
    }

    @Test
    @DisplayName("Eine Dauer unter 15 Minuten wird mit 400 abgelehnt")
    void zuKurzeDauerWirdMit400Abgelehnt() throws Exception {
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, beginn.toInstant(), beginn.plusMinutes(10).toInstant())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Dauer muss zwischen 15 Minuten und 8 Stunden liegen."));

        assertKeineReservation();
    }

    @Test
    @DisplayName("Eine Dauer über 8 Stunden wird mit 400 abgelehnt")
    void zuLangeDauerWirdMit400Abgelehnt() throws Exception {
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, beginn.toInstant(), beginn.plusHours(9).toInstant())))
                .andExpect(status().isBadRequest());

        assertKeineReservation();
    }

    @Test
    @DisplayName("Genau 15 Minuten und genau 8 Stunden sind erlaubt (Grenzwerte)")
    void grenzwerteDerDauerSindErlaubt() throws Exception {
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, beginn.toInstant(), beginn.plusMinutes(15).toInstant())))
                .andExpect(status().isCreated());

        ZonedDateTime morgen = beginn.plusDays(1);
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, morgen.toInstant(), morgen.plusHours(8).toInstant())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Ein Ende vor dem Beginn wird mit 400 abgelehnt")
    void endeVorBeginnWirdMit400Abgelehnt() throws Exception {
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, beginn.toInstant(), beginn.minusHours(1).toInstant())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Ende muss nach dem Beginn liegen."));
    }

    @Test
    @DisplayName("Ein Beginn in der Vergangenheit wird mit 400 abgelehnt")
    void beginnInVergangenheitWirdMit400Abgelehnt() throws Exception {
        Instant gestern = Instant.now().minus(Duration.ofDays(1));

        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, gestern, gestern.plus(Duration.ofHours(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Beginn darf nicht in der Vergangenheit liegen."));
    }

    @Test
    @DisplayName("Ein unbekannter Zweck wird mit 400 abgelehnt")
    void unbekannterZweckWirdMit400Abgelehnt() throws Exception {
        String body = body(anlageId, technikerId, beginn.toInstant(), beginn.plusHours(1).toInstant())
                .replace("\"UPDATE\"", "\"KAFFEEPAUSE\"");

        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Eine unbekannte Reservation liefert 404")
    void unbekannteReservationLiefert404() throws Exception {
        mockMvc.perform(get("/api/reservationen/{id}", 999))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A4: Eine überlappende Reservation auf derselben Anlage wird mit 409 abgelehnt")
    void ueberlappendeReservationAufAnlageWirdAbgelehnt() throws Exception {
        anlegen(anlageId, technikerId, beginn, beginn.plusHours(2));
        long andererTechniker = technikerAnlegen("BXY", true);

        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, andererTechniker, beginn.plusHours(1).toInstant(), beginn.plusHours(3).toInstant())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Anlage ANL-0815 ist im gewuenschten Zeitraum bereits reserviert."));
    }

    @Test
    @DisplayName("A4: Ein Techniker kann nicht gleichzeitig auf zwei Anlagen eingeplant werden (409)")
    void technikerDoppeltEingeplantWirdAbgelehnt() throws Exception {
        anlegen(anlageId, technikerId, beginn, beginn.plusHours(2));
        long andereAnlage = anlageAnlegen("ANL-0816", true);

        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(andereAnlage, technikerId, beginn.plusMinutes(30).toInstant(), beginn.plusHours(1).toInstant())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Techniker AMU ist im gewuenschten Zeitraum bereits eingeplant."));
    }

    @Test
    @DisplayName("A4: Direkt aneinandergrenzende Fenster (Ende = Beginn) sind zulässig")
    void aneinandergrenzendeFensterSindZulaessig() throws Exception {
        anlegen(anlageId, technikerId, beginn, beginn.plusHours(2));

        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, beginn.plusHours(2).toInstant(), beginn.plusHours(3).toInstant())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A4/T5: Zwei gleichzeitige, überlappende Anfragen ergeben genau eine Reservation und ein 409")
    void gleichzeitigeDoppelreservationErgibtGenauEineReservation() throws Exception {
        long zweiterTechniker = technikerAnlegen("BXY", true);
        String anfrage1 = body(anlageId, technikerId, beginn.toInstant(), beginn.plusHours(2).toInstant());
        String anfrage2 = body(anlageId, zweiterTechniker, beginn.plusHours(1).toInstant(), beginn.plusHours(3).toInstant());

        // Beide Threads warten am Startsignal und schicken ihre Anfrage dann moeglichst gleichzeitig ab.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> status1 = pool.submit(() -> senden(start, anfrage1));
            Future<Integer> status2 = pool.submit(() -> senden(start, anfrage2));
            start.countDown();

            // Egal, wer gewinnt: genau eine Anfrage ist erfolgreich, die andere erhaelt 409
            // (entweder durch die Vorabpruefung oder durch den Ausschluss-Constraint aus V4).
            assertThat(List.of(status1.get(10, TimeUnit.SECONDS), status2.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM statusereignis", Integer.class)).isEqualTo(1);
    }

    private int senden(CountDownLatch start, String body) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private void anlegen(long anlageId, long technikerId, ZonedDateTime von, ZonedDateTime bis) throws Exception {
        mockMvc.perform(post("/api/reservationen").contentType(APPLICATION_JSON)
                        .content(body(anlageId, technikerId, von.toInstant(), bis.toInstant())))
                .andExpect(status().isCreated());
    }

    private String body(long anlageId, long technikerId, Instant beginn, Instant ende) {
        return """
                {
                  "anlageId":    %d,
                  "technikerId": %d,
                  "beginn":      "%s",
                  "ende":        "%s",
                  "zweck":       "UPDATE",
                  "bemerkung":   "Firmware 2.4"
                }
                """.formatted(anlageId, technikerId, beginn, ende);
    }

    private static String iso(ZonedDateTime zeit) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zeit.toOffsetDateTime());
    }

    private void assertKeineReservation() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM statusereignis", Integer.class)).isZero();
    }

    private long anlageAnlegen(String anlagennummer, boolean aktiv) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp, aktiv)
                VALUES (?, ?, 'Palettierer Halle 2', 'S7-1500', ?) RETURNING id
                """, Long.class, kundeId, anlagennummer, aktiv));
    }

    private long technikerAnlegen(String kuerzel, boolean aktiv) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO techniker (kuerzel, vorname, nachname, aktiv) VALUES (?, 'Test', 'Person', ?) RETURNING id",
                Long.class, kuerzel, aktiv));
    }
}
