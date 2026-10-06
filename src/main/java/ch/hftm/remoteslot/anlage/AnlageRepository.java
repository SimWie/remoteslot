package ch.hftm.remoteslot.anlage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnlageRepository extends JpaRepository<Anlage, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Anlage a WHERE a.id = :id")
    Optional<Anlage> findByIdGesperrt(@Param("id") Long id);

    /**
     * Gemeinsame Sperre (SELECT ... FOR SHARE) beim Reservieren: blockiert ein gleichzeitiges
     * Ausser-Betrieb-Nehmen bzw. Deaktivieren, aber keine anderen Reservationen.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Anlage a WHERE a.id = :id")
    Optional<Anlage> findByIdZumReservieren(@Param("id") Long id);

    boolean existsByAnlagennummerAndAktivTrue(String anlagennummer);

}