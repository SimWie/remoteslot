package ch.hftm.remoteslot.anlage;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A1: Anlagen verwalten und ausser Betrieb nehmen. Gueltige und ungueltige Eingaben gegen echtes PostgreSQL. */
@SuppressWarnings("null")
class AnlageApiTest extends AbstractIntegrationTest {

    private long kundeId;

    @BeforeEach
    void setUp() {
        tabellenLeeren();
        kundeId = kundeAnlegen("Lonza AG", "Visp");
    }

    @Test
    @DisplayName("Eine Anlage kann erfasst werden und ist danach aktiv")
    void erfasstAnlage() throws Exception {
        mockMvc.perform(post("/api/anlagen").contentType(APPLICATION_JSON).content(erfassenBody(kundeId, "ANL-0815")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.kundeId").value(kundeId))
                .andExpect(jsonPath("$.anlagennummer").value("ANL-0815"))
                .andExpect(jsonPath("$.aktiv").value(true));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM anlage", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Eine Anlage für einen unbekannten Kunden wird mit 404 abgelehnt")
    void unbekannterKundeLiefert404() throws Exception {
        mockMvc.perform(post("/api/anlagen").contentType(APPLICATION_JSON).content(erfassenBody(999, "ANL-0815")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Kunde 999 nicht gefunden."));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM anlage", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Eine Anlagennummer, die schon einer aktiven Anlage gehört, wird mit 409 abgelehnt")
    void doppelteAktiveAnlagennummerWirdMit409Abgelehnt() throws Exception {
        anlageAnlegen("ANL-0815", true);

        mockMvc.perform(post("/api/anlagen").contentType(APPLICATION_JSON).content(erfassenBody(kundeId, "ANL-0815")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Anlagennummer ANL-0815 ist bereits einer aktiven Anlage zugeordnet."));
    }

    @Test
    @DisplayName("Kundenwechsel: Die Nummer einer ausser Betrieb genommenen Anlage kann neu erfasst werden")
    void nummerEinerInaktivenAnlageKannNeuErfasstWerden() throws Exception {
        anlageAnlegen("ANL-0815", false);
        long neuerKundeId = kundeAnlegen("Novartis AG", "Basel");

        mockMvc.perform(post("/api/anlagen").contentType(APPLICATION_JSON).content(erfassenBody(neuerKundeId, "ANL-0815")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kundeId").value(neuerKundeId));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM anlage WHERE anlagennummer = 'ANL-0815'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("Eine Anlagennummer mit ungültigem Format wird mit 400 abgelehnt")
    void ungueltigeAnlagennummerWirdMit400Abgelehnt() throws Exception {
        mockMvc.perform(post("/api/anlagen").contentType(APPLICATION_JSON).content(erfassenBody(kundeId, "anl 1")))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM anlage", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Eine geladene Anlage liefert die Kunden-ID, ohne den Kunden nachzuladen")
    void geladeneAnlageLiefertKundenId() throws Exception {
        long id = anlageAnlegen("ANL-0815", true);

        mockMvc.perform(get("/api/anlagen/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kundeId").value(kundeId))
                .andExpect(jsonPath("$.bezeichnung").value("Palettierer Halle 2"));
    }

    @Test
    @DisplayName("Bezeichnung und Steuerungstyp können geändert werden und stehen danach in der Datenbank")
    void anlageKannGeaendertWerden() throws Exception {
        long id = anlageAnlegen("ANL-0815", true);
        String body = """
                {
                  "bezeichnung":   "Palettierer Halle 3",
                  "steuerungstyp": "S7-1200"
                }
                """;

        mockMvc.perform(put("/api/anlagen/{id}", id).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bezeichnung").value("Palettierer Halle 3"))
                .andExpect(jsonPath("$.anlagennummer").value("ANL-0815"));

        assertThat(jdbc.queryForObject("SELECT steuerungstyp FROM anlage WHERE id = ?", String.class, id))
                .isEqualTo("S7-1200");
    }

    @Test
    @DisplayName("Eine Anlage kann ausser Betrieb genommen werden")
    void anlageKannAusserBetriebGenommenWerden() throws Exception {
        long id = anlageAnlegen("ANL-0815", true);

        mockMvc.perform(post("/api/anlagen/{id}/ausser-betrieb", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiv").value(false));

        assertThat(jdbc.queryForObject("SELECT aktiv FROM anlage WHERE id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    @DisplayName("Eine bereits ausser Betrieb genommene Anlage kann nicht erneut ausser Betrieb genommen werden")
    void zweimalAusserBetriebNehmenWirdMit409Abgelehnt() throws Exception {
        long id = anlageAnlegen("ANL-0815", false);

        mockMvc.perform(post("/api/anlagen/{id}/ausser-betrieb", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Anlage ANL-0815 ist bereits ausser Betrieb."));
    }

    @Test
    @DisplayName("Eine unbekannte Anlage liefert 404")
    void unbekannteAnlageLiefert404() throws Exception {
        mockMvc.perform(get("/api/anlagen/{id}", 999))
                .andExpect(status().isNotFound());
    }

    private String erfassenBody(long kundeId, String anlagennummer) {
        return """
                {
                  "kundeId":       %d,
                  "anlagennummer": "%s",
                  "bezeichnung":   "Palettierer Halle 2",
                  "steuerungstyp": "S7-1500"
                }
                """.formatted(kundeId, anlagennummer);
    }

    private long kundeAnlegen(String name, String ort) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES (?, ?) RETURNING id", Long.class, name, ort));
    }

    private long anlageAnlegen(String anlagennummer, boolean aktiv) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp, aktiv)
                VALUES (?, ?, 'Palettierer Halle 2', 'S7-1500', ?) RETURNING id
                """, Long.class, kundeId, anlagennummer, aktiv));
    }
}
