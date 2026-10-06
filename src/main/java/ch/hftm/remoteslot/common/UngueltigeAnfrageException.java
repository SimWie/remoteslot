package ch.hftm.remoteslot.common;

/** Fachlich ungueltige Eingabe, die sich nicht per Bean Validation pruefen laesst. Wird als HTTP 400 gemeldet. */
public class UngueltigeAnfrageException extends RuntimeException {
    public UngueltigeAnfrageException(String message) {
        super(message);
    }
}
