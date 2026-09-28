package ch.hftm.remoteslot.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

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

    @ExceptionHandler(KonfliktException.class)
    ProblemDetail konflikt(KonfliktException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail veraltet(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Der Datensatz wurde inzwischen geaendert. Bitte neu laden und die Aenderung wiederholen.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail datenbankregel(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Die Aenderung verletzt eine Datenbankregel.");
    }
}
