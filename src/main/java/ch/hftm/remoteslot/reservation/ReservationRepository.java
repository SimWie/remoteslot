package ch.hftm.remoteslot.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

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

    /** Wie anlageBelegt, aber ohne die Reservation selbst (fuer das Verschieben). */
    @Query("""
            SELECT count(r) > 0 FROM Reservation r
            WHERE r.anlage.id = :anlageId AND r.id <> :reservationId
              AND r.status <> ch.hftm.remoteslot.reservation.Status.STORNIERT
              AND r.beginn < :ende AND r.ende > :beginn
            """)
    boolean anlageBelegtAusser(@Param("anlageId") Long anlageId, @Param("reservationId") Long reservationId,
                               @Param("beginn") Instant beginn, @Param("ende") Instant ende);

    @Query("""
            SELECT count(r) > 0 FROM Reservation r
            WHERE r.techniker.id = :technikerId AND r.id <> :reservationId
              AND r.status <> ch.hftm.remoteslot.reservation.Status.STORNIERT
              AND r.beginn < :ende AND r.ende > :beginn
            """)
    boolean technikerBelegtAusser(@Param("technikerId") Long technikerId, @Param("reservationId") Long reservationId,
                                  @Param("beginn") Instant beginn, @Param("ende") Instant ende);

    List<Reservation> findByAnlageIdAndStatusInOrderByBeginnAscIdAsc(Long anlageId, Collection<Status> status);
}
