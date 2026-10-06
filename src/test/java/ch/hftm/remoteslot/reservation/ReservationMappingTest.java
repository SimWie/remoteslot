package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.AbstractIntegrationTest;
import ch.hftm.remoteslot.anlage.Anlage;
import ch.hftm.remoteslot.anlage.AnlageRepository;
import ch.hftm.remoteslot.common.KonfliktException;
import ch.hftm.remoteslot.techniker.Techniker;
import ch.hftm.remoteslot.techniker.TechnikerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T3: JPA-Mapping von Reservation und Statusereignis gegen echtes PostgreSQL (ohne API). */
@SuppressWarnings("null")
class ReservationMappingTest extends AbstractIntegrationTest {

    private static final Instant BEGINN = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant ENDE = Instant.parse("2026-11-02T10:00:00Z");

    @Autowired ReservationRepository reservationen;
    @Autowired StatusereignisRepository statusereignisse;
    @Autowired AnlageRepository anlagen;
    @Autowired TechnikerRepository techniker;

    private Anlage anlage;
    private Techniker anna;

    @BeforeEach
    void setUp() {
        tabellenLeeren();
        long kundeId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO kunde (name, ort) VALUES ('Lonza AG', 'Visp') RETURNING id", Long.class));
        long anlageId = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO anlage (kunde_id, anlagennummer, bezeichnung, steuerungstyp)
                VALUES (?, 'ANL-0815', 'Palettierer Halle 2', 'S7-1500') RETURNING id
                """, Long.class, kundeId));
        long technikerId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO techniker (kuerzel, vorname, nachname) VALUES ('AMU', 'Anna', 'Muster') RETURNING id", Long.class));
        anlage = anlagen.findById(anlageId).orElseThrow();
        anna = techniker.findById(technikerId).orElseThrow();
    }

    @Test
    @DisplayName("Eine neue Reservation wird als GEPLANT mit Version 0 und erstem Verlaufseintrag gespeichert")
    void neueReservationWirdMitErstemVerlaufseintragGespeichert() {
        Reservation reservation = new Reservation(anlage, anna, BEGINN, ENDE, Zweck.UPDATE, "Firmware 2.4");

        reservationen.saveAndFlush(reservation);
        statusereignisse.saveAndFlush(reservation.erfassungsereignis());

        // Enums werden als Text gespeichert, damit die CHECK-Constraints aus V1 greifen
        var zeile = jdbc.queryForMap("SELECT status, zweck, version, erstellt_am FROM reservation WHERE id = ?",
                reservation.getId());
        assertThat(zeile.get("status")).isEqualTo("GEPLANT");
        assertThat(zeile.get("zweck")).isEqualTo("UPDATE");
        assertThat(zeile.get("version")).isEqualTo(0L);
        assertThat(zeile.get("erstellt_am")).isNotNull();

        var ereignis = jdbc.queryForMap(
                "SELECT alter_status, neuer_status FROM statusereignis WHERE reservation_id = ?", reservation.getId());
        assertThat(ereignis.get("alter_status")).isNull();
        assertThat(ereignis.get("neuer_status")).isEqualTo("GEPLANT");
    }

    @Test
    @DisplayName("Ein Statuswechsel erhöht die Version und schreibt einen Verlaufseintrag mit altem und neuem Status")
    void statuswechselErhoehtVersionUndSchreibtVerlauf() {
        long id = reservationAnlegen();

        Reservation geladen = reservationen.findById(id).orElseThrow();
        Statusereignis ereignis = geladen.statusWechseln(Status.AKTIV, "Verbindung aufgebaut");
        reservationen.saveAndFlush(geladen);
        statusereignisse.saveAndFlush(ereignis);

        assertThat(jdbc.queryForObject("SELECT version FROM reservation WHERE id = ?", Long.class, id)).isEqualTo(1L);
        var verlauf = statusereignisse.findByReservationIdOrderByZeitpunktAscIdAsc(id);
        assertThat(verlauf).extracting(Statusereignis::getNeuerStatus).containsExactly(Status.GEPLANT, Status.AKTIV);
        assertThat(verlauf.get(1).getAlterStatus()).isEqualTo(Status.GEPLANT);
    }

    @Test
    @DisplayName("Ein unzulässiger Statuswechsel wird abgelehnt und ändert den Status nicht")
    void unzulaessigerStatuswechselWirdAbgelehnt() {
        Reservation reservation = new Reservation(anlage, anna, BEGINN, ENDE, Zweck.UPDATE, null);

        assertThatThrownBy(() -> reservation.statusWechseln(Status.ABGESCHLOSSEN, null))
                .isInstanceOf(KonfliktException.class)
                .hasMessage("Statuswechsel von GEPLANT zu ABGESCHLOSSEN ist nicht zulaessig.");
        assertThat(reservation.getStatus()).isEqualTo(Status.GEPLANT);
    }

    @Test
    @DisplayName("Eine Änderung auf einem veralteten Stand wird abgelehnt (optimistisches Sperren)")
    void aenderungAufVeraltetemStandWirdAbgelehnt() {
        long id = reservationAnlegen();
        Reservation disponentA = reservationen.findById(id).orElseThrow();
        Reservation disponentB = reservationen.findById(id).orElseThrow();   // beide sehen Version 0

        disponentA.statusWechseln(Status.AKTIV, null);
        reservationen.saveAndFlush(disponentA);                               // Version 0 -> 1

        disponentB.statusWechseln(Status.STORNIERT, null);
        assertThatThrownBy(() -> reservationen.saveAndFlush(disponentB))      // B arbeitet noch mit Version 0
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, id))
                .isEqualTo("AKTIV");
    }

    private long reservationAnlegen() {
        Reservation reservation = new Reservation(anlage, anna, BEGINN, ENDE, Zweck.UPDATE, null);
        reservationen.saveAndFlush(reservation);
        statusereignisse.saveAndFlush(reservation.erfassungsereignis());
        return reservation.getId();
    }
}
