package ch.hftm.remoteslot.anlage;

import ch.hftm.remoteslot.common.KonfliktException;
import ch.hftm.remoteslot.common.NichtGefundenException;
import ch.hftm.remoteslot.kunde.Kunde;
import ch.hftm.remoteslot.kunde.KundeService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnlageService {

    private final AnlageRepository repository;
    private final KundeService kundeService;

    public AnlageService(AnlageRepository repository, KundeService kundeService) {
        this.repository = repository;
        this.kundeService = kundeService;
    }

    @Transactional
    public Anlage erfassen(@NonNull Long kundeId, String anlagennummer, String bezeichnung, String steuerungstyp) {
        // Unbekannter Kunde ergibt 404 (NichtGefundenException aus dem KundeService).
        Kunde kunde = kundeService.laden(kundeId);

        // Vorabpruefung nur fuer eine verstaendliche Meldung.
        // Die eigentliche Garantie ist der partielle Unique-Index aus V2.
        if (repository.existsByAnlagennummerAndAktivTrue(anlagennummer)) {
            throw new KonfliktException("Anlagennummer " + anlagennummer + " ist bereits einer aktiven Anlage zugeordnet.");
        }
        return repository.saveAndFlush(new Anlage(kunde, anlagennummer, bezeichnung.strip(), steuerungstyp.strip()));
    }

    public Anlage laden(@NonNull Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NichtGefundenException("Anlage " + id + " nicht gefunden."));
    }

    @Transactional
    public Anlage aendern(@NonNull Long id, String bezeichnung, String steuerungstyp) {
        // Keine Eindeutigkeitspruefung noetig: Bezeichnung und Steuerungstyp haben keinen Constraint.
        Anlage anlage = laden(id);
        anlage.aendern(bezeichnung.strip(), steuerungstyp.strip());
        return anlage;
    }

    /**
     * Kritische Geschaeftsoperation (T4). Die Anlage wird gesperrt geladen (SELECT ... FOR UPDATE),
     * damit gleichzeitige Reservationen auf dieser Anlage warten muessen und danach den neuen Stand sehen.
     */
    @Transactional
    public Anlage ausserBetriebNehmen(@NonNull Long id) {
        Anlage anlage = repository.findByIdGesperrt(id)
                .orElseThrow(() -> new NichtGefundenException("Anlage " + id + " nicht gefunden."));
        anlage.ausserBetriebNehmen();
        // TODO A3: alle GEPLANT-Reservationen der Anlage stornieren und je ein Statusereignis
        //          mit der Bemerkung "Anlage ausser Betrieb genommen" schreiben (gleiche Transaktion).
        return anlage;
    }
}
