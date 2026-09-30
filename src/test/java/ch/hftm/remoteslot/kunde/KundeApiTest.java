package ch.hftm.remoteslot.kunde;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A1: Kunden verwalten. Gueltige und ungueltige Eingaben gegen echtes PostgreSQL. */
@SuppressWarnings("null")
class KundeApiTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        tabellenLeeren();
    }

    @Test
    @DisplayName("Ein Kunde kann erfasst werden und landet in der Datenbank")
    void erfasstKunde() throws Exception {
        String body = """
                {
                  "name": "Lonza AG",
                  "ort":  "Visp"
                }
                """;

        mockMvc.perform(post("/api/kunden").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Lonza AG"))
                .andExpect(jsonPath("$.ort").value("Visp"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM kunde", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Beim Erfassen werden Leerzeichen am Rand entfernt")
    void leerzeichenAmRandWerdenEntfernt() throws Exception {
        String body = """
                {
                  "name": "  Lonza AG ",
                  "ort":  " Visp "
                }
                """;

        mockMvc.perform(post("/api/kunden").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Lonza AG"))
                .andExpect(jsonPath("$.ort").value("Visp"));

        assertThat(jdbc.queryForObject("SELECT name FROM kunde", String.class)).isEqualTo("Lonza AG");
    }

    @Test
    @DisplayName("Ein doppelter Kunde (gleicher Name und Ort) wird mit 409 abgelehnt")
    void doppelterKundeWirdMit409Abgelehnt() throws Exception {
        kundeAnlegen("Lonza AG", "Visp");
        String body = """
                {
                  "name": "Lonza AG",
                  "ort":  "Visp"
                }
                """;

        mockMvc.perform(post("/api/kunden").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Kunde Lonza AG in Visp ist bereits erfasst."));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM kunde", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Derselbe Kunde in anderer Schreibweise wird mit 409 abgelehnt")
    void kundeInAndererSchreibweiseWirdMit409Abgelehnt() throws Exception {
        kundeAnlegen("Lonza AG", "Visp");
        String body = """
                {
                  "name": " lonza ag ",
                  "ort":  "VISP"
                }
                """;

        mockMvc.perform(post("/api/kunden").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM kunde", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Ein Name nur aus Leerzeichen wird mit 400 abgelehnt")
    void leererNameWirdMit400Abgelehnt() throws Exception {
        String body = """
                {
                  "name": "   ",
                  "ort":  "Visp"
                }
                """;

        mockMvc.perform(post("/api/kunden").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM kunde", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Ein Kunde kann geändert werden und die Änderung steht in der Datenbank")
    void kundeKannGeaendertWerden() throws Exception {
        long id = kundeAnlegen("Lonza AG", "Visp");
        String body = """
                {
                  "name": "Lonza Group AG",
                  "ort":  "Basel"
                }
                """;

        mockMvc.perform(put("/api/kunden/{id}", id).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lonza Group AG"))
                .andExpect(jsonPath("$.ort").value("Basel"));

        // Direkt in der DB nachlesen: prueft, dass die Aenderung wirklich geschrieben wurde
        assertThat(jdbc.queryForObject("SELECT name FROM kunde WHERE id = ?", String.class, id))
                .isEqualTo("Lonza Group AG");
    }

    @Test
    @DisplayName("Ändern auf Name und Ort eines anderen Kunden wird mit 409 abgelehnt")
    void aendernAufBestehendenKundenWirdMit409Abgelehnt() throws Exception {
        kundeAnlegen("Lonza AG", "Visp");
        long id = kundeAnlegen("Novartis AG", "Basel");
        String body = """
                {
                  "name": "Lonza AG",
                  "ort":  "Visp"
                }
                """;

        mockMvc.perform(put("/api/kunden/{id}", id).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT name FROM kunde WHERE id = ?", String.class, id))
                .isEqualTo("Novartis AG");
    }

    @Test
    @DisplayName("Ändern ohne Namenswechsel ist kein Konflikt mit sich selbst")
    void aendernOhneNamenswechselIstErlaubt() throws Exception {
        long id = kundeAnlegen("Lonza AG", "Visp");
        String body = """
                {
                  "name": "Lonza AG",
                  "ort":  "Visp"
                }
                """;

        mockMvc.perform(put("/api/kunden/{id}", id).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ein unbekannter Kunde liefert 404")
    void unbekannterKundeLiefert404() throws Exception {
        mockMvc.perform(get("/api/kunden/{id}", 999))
                .andExpect(status().isNotFound());
    }

    private long kundeAnlegen(String name, String ort) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES (?, ?) RETURNING id", Long.class, name, ort));
    }
}
