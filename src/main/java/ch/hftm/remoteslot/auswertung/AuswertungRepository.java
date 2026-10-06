package ch.hftm.remoteslot.auswertung;

import ch.hftm.remoteslot.auswertung.AuswertungDtos.AufwandKundeMonat;
import ch.hftm.remoteslot.auswertung.AuswertungDtos.AufwandTechnikerZweck;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * T6: Auswertungen bewusst mit JDBC und eigenem SQL statt JPA.
 * Aggregationen (GROUP BY, SUM, COUNT) sind in SQL direkt und lesbar formulierbar; es werden keine
 * Entities geladen, sondern nur die fertigen Summen. Grundlage ist die View v_einsatz (V6).
 */
@Repository
public class AuswertungRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AuswertungRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Einsaetze mit Beginn in [von, bis). */
    public List<AufwandKundeMonat> aufwandJeKundeUndMonat(OffsetDateTime von, OffsetDateTime bis) {
        String sql = """
                SELECT k.id                      AS kunde_id,
                       k.name                    AS kunde,
                       k.ort,
                       e.monat,
                       count(*)                  AS einsaetze,
                       round(sum(e.stunden), 2)  AS stunden
                FROM v_einsatz e
                JOIN kunde k ON k.id = e.kunde_id
                WHERE e.beginn >= :von AND e.beginn < :bis
                GROUP BY k.id, k.name, k.ort, e.monat
                ORDER BY k.name, k.id, e.monat
                """;
        return jdbc.query(sql, zeitraum(von, bis), (rs, i) -> new AufwandKundeMonat(
                rs.getLong("kunde_id"),
                rs.getString("kunde"),
                rs.getString("ort"),
                YearMonth.from(rs.getObject("monat", LocalDate.class)),
                rs.getLong("einsaetze"),
                rs.getBigDecimal("stunden")));
    }

    /** Einsaetze mit Beginn in [von, bis). */
    public List<AufwandTechnikerZweck> aufwandJeTechnikerUndZweck(OffsetDateTime von, OffsetDateTime bis) {
        String sql = """
                SELECT t.id                      AS techniker_id,
                       t.kuerzel,
                       e.zweck,
                       count(*)                  AS einsaetze,
                       round(sum(e.stunden), 2)  AS stunden
                FROM v_einsatz e
                JOIN techniker t ON t.id = e.techniker_id
                WHERE e.beginn >= :von AND e.beginn < :bis
                GROUP BY t.id, t.kuerzel, e.zweck
                ORDER BY t.kuerzel, e.zweck
                """;
        return jdbc.query(sql, zeitraum(von, bis), (rs, i) -> new AufwandTechnikerZweck(
                rs.getLong("techniker_id"),
                rs.getString("kuerzel"),
                rs.getString("zweck"),
                rs.getLong("einsaetze"),
                rs.getBigDecimal("stunden")));
    }

    private static MapSqlParameterSource zeitraum(OffsetDateTime von, OffsetDateTime bis) {
        return new MapSqlParameterSource().addValue("von", von).addValue("bis", bis);
    }
}
