package ch.hftm.remoteslot.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/**
 * Uebersetzt Fehler in einheitliche Problem-Details (RFC 9457).
 * Validierungsfehler (400) behandelt die Basisklasse.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NichtGefundenException.class)
    ProblemDetail nichtGefunden(NichtGefundenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UngueltigeAnfrageException.class)
    ProblemDetail ungueltig(UngueltigeAnfrageException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(KonfliktException.class)
    ProblemDetail konflikt(KonfliktException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail veraltet(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Der Datensatz wurde inzwischen geaendert. Bitte neu laden und die Aenderung wiederholen.");
    }

    /**
     * Verstaendliche Meldungen fuer bekannte Constraints. Greift, wenn die Vorabpruefung im Service
     * umgangen wurde, z. B. bei zwei gleichzeitigen Anfragen. Schluessel = Constraint- bzw. Indexname.
     */
    private static final Map<String, String> CONSTRAINT_MELDUNGEN = Map.of(
            "ex_reservation_anlage_ueberschneidung", "Die Anlage ist im gewuenschten Zeitraum bereits reserviert.",
            "ex_reservation_techniker_ueberschneidung", "Der Techniker ist im gewuenschten Zeitraum bereits eingeplant.",
            "uq_anlage_anlagennummer_aktiv", "Die Anlagennummer ist bereits einer aktiven Anlage zugeordnet.",
            "uq_kunde_name_ort", "Ein Kunde mit diesem Namen und Ort ist bereits erfasst.",
            "uq_techniker_kuerzel", "Das Kuerzel ist bereits vergeben.");

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail datenbankregel(DataIntegrityViolationException ex) {
        // Die Meldung von PostgreSQL enthaelt den Namen des verletzten Constraints.
        String ursache = String.valueOf(ex.getMostSpecificCause().getMessage());
        String meldung = CONSTRAINT_MELDUNGEN.entrySet().stream()
                .filter(e -> ursache.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("Die Aenderung verletzt eine Datenbankregel.");
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, meldung);
    }
}
