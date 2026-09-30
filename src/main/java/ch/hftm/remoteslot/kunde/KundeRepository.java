package ch.hftm.remoteslot.kunde;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KundeRepository extends JpaRepository<Kunde, Long> {

    boolean existsByNameIgnoreCaseAndOrtIgnoreCase(String name, String ort);

    boolean existsByNameIgnoreCaseAndOrtIgnoreCaseAndIdNot(String name, String ort, Long id);
}
