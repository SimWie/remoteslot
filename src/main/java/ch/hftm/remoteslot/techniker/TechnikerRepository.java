package ch.hftm.remoteslot.techniker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface TechnikerRepository extends JpaRepository<Techniker, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Techniker t WHERE t.id = :id")
    Optional<Techniker> findByIdGesperrt(@Param("id") Long id);

    /**
     * Gemeinsame Sperre (SELECT ... FOR SHARE) beim Reservieren: blockiert ein gleichzeitiges
     * Ausser-Betrieb-Nehmen bzw. Deaktivieren, aber keine anderen Reservationen.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT t FROM Techniker t WHERE t.id = :id")
    Optional<Techniker> findByIdZumReservieren(@Param("id") Long id);

    boolean existsByKuerzel(String kuerzel);
}
