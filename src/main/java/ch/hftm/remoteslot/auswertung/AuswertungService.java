package ch.hftm.remoteslot.auswertung;

import ch.hftm.remoteslot.auswertung.AuswertungDtos.AufwandKundeMonat;
import ch.hftm.remoteslot.auswertung.AuswertungDtos.AufwandTechnikerZweck;
import ch.hftm.remoteslot.common.UngueltigeAnfrageException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** A7: Fernwartungsaufwand auswerten. Der Zeitraum wird als Kalendertage in Schweizer Zeit angegeben. */
@Service
@Transactional(readOnly = true)
public class AuswertungService {

    private static final ZoneId SCHWEIZ = ZoneId.of("Europe/Zurich");

    private final AuswertungRepository repository;

    public AuswertungService(AuswertungRepository repository) {
        this.repository = repository;
    }

    /** von inklusive, bis exklusive (z. B. von=2026-10-01, bis=2026-11-01 ergibt den Oktober). */
    public List<AufwandKundeMonat> aufwandJeKunde(LocalDate von, LocalDate bis) {
        pruefe(von, bis);
        return repository.aufwandJeKundeUndMonat(tagesbeginn(von), tagesbeginn(bis));
    }

    public List<AufwandTechnikerZweck> aufwandJeTechniker(LocalDate von, LocalDate bis) {
        pruefe(von, bis);
        return repository.aufwandJeTechnikerUndZweck(tagesbeginn(von), tagesbeginn(bis));
    }

    private static void pruefe(LocalDate von, LocalDate bis) {
        if (!bis.isAfter(von)) {
            throw new UngueltigeAnfrageException("'bis' muss nach 'von' liegen.");
        }
    }

    /** Mitternacht in Schweizer Zeit, damit die Tagesgrenzen zur Zuordnung nach Monat (V6) passen. */
    private static OffsetDateTime tagesbeginn(LocalDate tag) {
        return tag.atStartOfDay(SCHWEIZ).toOffsetDateTime();
    }
}
