package ch.hftm.remoteslot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Gemeinsame Basis: gleiche Konfiguration = ein Spring-Kontext und ein Container fuer alle Tests. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Kleine, kontrollierte Testdaten: jede Testklasse startet mit leeren Fachtabellen. */
    protected void tabellenLeeren() {
        jdbc.execute("TRUNCATE statusereignis, reservation, anlage, kunde, techniker RESTART IDENTITY CASCADE");
    }
}
