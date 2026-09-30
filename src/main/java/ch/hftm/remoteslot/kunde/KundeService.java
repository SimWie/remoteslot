package ch.hftm.remoteslot.kunde;

import ch.hftm.remoteslot.common.KonfliktException;
import ch.hftm.remoteslot.common.NichtGefundenException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service 
@Transactional(readOnly = true)
public class KundeService {
    
    private final KundeRepository repository;

    public KundeService(KundeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Kunde erfassen(String name, String ort) {
        String n = name.strip();
        String o = ort.strip();

        // Vorabpruefung nur fuer eine verstaendliche Meldung.
        // Die eigentliche Garantie ist der Unique-Index aus V3
        // (zwei gleichzeitige Anfragen koennen diese Pruefung beide bestehen).
        if (repository.existsByNameIgnoreCaseAndOrtIgnoreCase(n, o)) {
            throw new KonfliktException("Kunde " + n + " in " + o + " ist bereits erfasst.");
        }
        return repository.saveAndFlush(new Kunde(n, o));
    }

    public Kunde laden(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NichtGefundenException("Kunde " + id + " nicht gefunden."));
    }

    @Transactional 
    public Kunde aendern(Long id, String name, String ort) {
        Kunde kunde = laden(id);
        String n = name.strip();
        String o = ort.strip();

        // Vorabpruefung wie beim Erfassen; IdNot schliesst den Kunden selbst aus.
        if (repository.existsByNameIgnoreCaseAndOrtIgnoreCaseAndIdNot(n, o, id)) {
            throw new KonfliktException("Kunde " + n + " in " + o + " ist bereits erfasst.");
        }
        kunde.aendern(n, o);
        return kunde;
    }
}
