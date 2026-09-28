package ch.hftm.remoteslot.techniker;

import ch.hftm.remoteslot.common.KonfliktException;
import ch.hftm.remoteslot.common.NichtGefundenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TechnikerService {

    private final TechnikerRepository repository;

    public TechnikerService(TechnikerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Techniker erfassen(String kuerzel, String vorname, String nachname) {
        // Vorabpruefung nur fuer eine verstaendliche Meldung.
        // Die eigentliche Garantie ist der UNIQUE-Constraint in der Datenbank
        // (zwei gleichzeitige Anfragen koennen diese Pruefung beide bestehen).
        if (repository.existsByKuerzel(kuerzel)) {
            throw new KonfliktException("Kuerzel " + kuerzel + " ist bereits vergeben.");
        }
        return repository.saveAndFlush(new Techniker(kuerzel, vorname, nachname));
    }

    public Techniker laden(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NichtGefundenException("Techniker " + id + " nicht gefunden."));
    }

    @Transactional
    public Techniker aendern(Long id, String vorname, String nachname) {
        Techniker techniker = laden(id);
        techniker.aendern(vorname, nachname);
        return techniker;
    }

    @Transactional
    public Techniker deaktivieren(Long id) {
        Techniker techniker = repository.findByIdGesperrt(id)
                .orElseThrow(() -> new NichtGefundenException("Techniker " + id + " nicht gefunden."));
        techniker.deaktivieren();
        return techniker;
    }
}
