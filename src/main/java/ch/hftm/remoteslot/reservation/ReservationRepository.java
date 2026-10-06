package ch.hftm.remoteslot.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Ueberschneidung halboffen wie im Constraint aus V4: [beginn, ende). Stornierte zaehlen nicht. */
    @Query("""
            SELECT count(r) > 0 FROM Reservation r
            WHERE r.anlage.id = :anlageId AND r.status <> ch.hftm.remoteslot.reservation.Status.STORNIERT
              AND r.beginn < :ende AND r.ende > :beginn
            """)
    boolean anlageBelegt(@Param("anlageId") Long anlageId, @Param("beginn") Instant beginn, @Param("ende") Instant ende);

    @Query("""
            SELECT count(r) > 0 FROM Reservation r
            WHERE r.techniker.id = :technikerId AND r.status <> ch.hftm.remoteslot.reservation.Status.STORNIERT
              AND r.beginn < :ende AND r.ende > :beginn
            """)
    boolean technikerBelegt(@Param("technikerId") Long technikerId, @Param("beginn") Instant beginn, @Param("ende") Instant ende);
}
