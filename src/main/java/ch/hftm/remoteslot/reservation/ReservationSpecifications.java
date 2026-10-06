package ch.hftm.remoteslot.reservation;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * A6: Bausteine fuer die Suche. Jeder Filter ist optional: ist der Wert null, liefert der Baustein null
 * und Spring Data laesst ihn weg. So entsteht nur SQL fuer die tatsaechlich gesetzten Filter.
 */
final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    static Specification<Reservation> kunde(Long kundeId) {
        // Der Kunde haengt an der Anlage (3. Normalform) -> Join reservation -> anlage
        return kundeId == null ? null
                : (r, q, cb) -> cb.equal(r.get("anlage").get("kunde").get("id"), kundeId);
    }

    static Specification<Reservation> anlage(Long anlageId) {
        return anlageId == null ? null : (r, q, cb) -> cb.equal(r.get("anlage").get("id"), anlageId);
    }

    static Specification<Reservation> techniker(Long technikerId) {
        return technikerId == null ? null : (r, q, cb) -> cb.equal(r.get("techniker").get("id"), technikerId);
    }

    static Specification<Reservation> status(Status status) {
        return status == null ? null : (r, q, cb) -> cb.equal(r.get("status"), status);
    }

    /**
     * Reservationen, die sich mit [von, bis) ueberschneiden (gleiche Logik wie A4): ende > von.
     *
     * T10: Zusaetzlich beginn > von - 8 h. Fachlich aendert das nichts, denn eine Reservation dauert hoechstens
     * 8 Stunden (CHECK in V1), aus ende > von folgt also immer beginn > von - 8 h. Die Bedingung gibt dem Index
     * auf (beginn, id) aber eine Untergrenze: ohne sie liest PostgreSQL den Index vom Anfang her durch.
     */
    static Specification<Reservation> abZeitpunkt(Instant von) {
        return von == null ? null : (r, q, cb) -> cb.and(
                cb.greaterThan(r.get("ende"), von),
                cb.greaterThan(r.get("beginn"), von.minus(ReservationService.MAX_DAUER)));
    }

    static Specification<Reservation> bisZeitpunkt(Instant bis) {
        return bis == null ? null : (r, q, cb) -> cb.lessThan(r.get("beginn"), bis);
    }
}
