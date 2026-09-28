package ch.hftm.remoteslot.techniker;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Objects;

/** A2: Techniker verwalten. Gueltige und ungueltige Eingaben gegen echtes PostgreSQL. */
class TechnikerApiTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        tabellenLeeren();
    }

    @Test
    @DisplayName("Ein Techniker kann erfasst werden und landet in der Datenbank")
    void erfasstTechniker() throws Exception {
        String body = """
                {
                  "kuerzel":  "AMU",
                  "vorname":  "Anna",
                  "nachname": "Muster"
                }
                """;

        mockMvc.perform(post("/api/techniker").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.kuerzel").value("AMU"))
                .andExpect(jsonPath("$.aktiv").value(true));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM techniker", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Ein doppeltes Kürzel wird mit 409 abgelehnt")
    void doppeltesKuerzelWirdMit409Abgelehnt() throws Exception {
        String body = """
                {
                  "kuerzel":  "AMU",
                  "vorname":  "Anna",
                  "nachname": "Muster"
                }
                """;

        mockMvc.perform(post("/api/techniker").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/techniker").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM techniker", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Ein Kürzel mit ungültigem Format wird mit 400 abgelehnt")
    void ungueltigesKuerzelWirdMit400Abgelehnt() throws Exception {
        String body = """
                {
                  "kuerzel":  "am1",
                  "vorname":  "Anna",
                  "nachname": "Muster"
                }
                """;

        mockMvc.perform(post("/api/techniker").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM techniker", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Die Datenbank erzwingt eindeutige Kürzel auch ohne API")
    void datenbankErzwingtEindeutigkeitAuchOhneApi() {
        jdbc.update("INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('AMU', 'Anna', 'Muster')");

        assertThatThrownBy(() ->
                jdbc.update("INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('AMU', 'Andreas', 'Muster')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Ein deaktivierter Techniker ist inaktiv")
    void deaktivierterTechnikerIstInaktiv() throws Exception {
        long id = technikerAnlegen(true);

        mockMvc.perform(post("/api/techniker/{id}/deaktivieren", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiv").value(false));
    }

    @Test
    @DisplayName("Ein deaktivierter Techniker kann nicht erneut deaktiviert werden")
    void deaktivierterTechnikerKannNichtErneutDeaktiviertWerden() throws Exception {
        long id = technikerAnlegen(false);

        mockMvc.perform(post("/api/techniker/{id}/deaktivieren", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Techniker AMU ist bereits deaktiviert."));
    }

    @Test
    @DisplayName("Ein unbekannter Techniker liefert 404")
    void unbekannterTechnikerLiefert404() throws Exception {
        mockMvc.perform(get("/api/techniker/{id}", 999))
                .andExpect(status().isNotFound());
    }

    /** Legt den Techniker AMU direkt per SQL an, aktiv oder bereits deaktiviert. */
    private long technikerAnlegen(boolean aktiv) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO techniker (kuerzel, vorname, nachname, aktiv) VALUES ('AMU', 'Anna', 'Muster', ?) RETURNING id",
                Long.class, aktiv));
    }
}
