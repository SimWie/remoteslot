package ch.hftm.remoteslot.common;

/** Fachlicher Konflikt, z. B. Verletzung einer Eindeutigkeit oder unzulaessiger Zustand. Wird als HTTP 409 gemeldet. */
public class KonfliktException extends RuntimeException {
    public KonfliktException(String message) {
        super(message);
    }
}
