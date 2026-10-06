package ch.hftm.remoteslot.reservation;

import java.util.Set;

/**
 * Status einer Reservation. Werte entsprechen ck_reservation_status in V1.
 * Zulaessige Uebergaenge (Steckbrief A5): GEPLANT -> AKTIV -> ABGESCHLOSSEN sowie GEPLANT -> STORNIERT.
 */
public enum Status {
    GEPLANT,
    AKTIV,
    ABGESCHLOSSEN,
    STORNIERT;

    public boolean darfWechselnZu(Status neu) {
        return switch (this) {
            case GEPLANT -> Set.of(AKTIV, STORNIERT).contains(neu);
            case AKTIV -> neu == ABGESCHLOSSEN;
            case ABGESCHLOSSEN, STORNIERT -> false;
        };
    }
}
