package ch.hftm.remoteslot.auswertung;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Ergebniszeilen der Auswertungen (A7). Direkt aus SQL befuellt, ohne Entities. */
public final class AuswertungDtos {

    private AuswertungDtos() {
    }

    /** Auswertung 1: Einsaetze und Stunden je Kunde und Monat. */
    public record AufwandKundeMonat(Long kundeId, String kunde, String ort, YearMonth monat,
                                    long einsaetze, BigDecimal stunden) {
    }

    /** Auswertung 2: Einsaetze und Stunden je Techniker, aufgeteilt nach Zweck. */
    public record AufwandTechnikerZweck(Long technikerId, String kuerzel, String zweck,
                                        long einsaetze, BigDecimal stunden) {
    }
}
