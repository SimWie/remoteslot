package ch.hftm.remoteslot.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusereignisRepository extends JpaRepository<Statusereignis, Long> {

    List<Statusereignis> findByReservationIdOrderByZeitpunktAscIdAsc(Long reservationId);
}
