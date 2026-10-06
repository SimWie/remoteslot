package ch.hftm.remoteslot.reservation;

/** Zweck eines Fernwartungseinsatzes. Werte entsprechen ck_reservation_zweck in V1. */
public enum Zweck {
    STOERUNG,
    UPDATE,
    INBETRIEBNAHME,
    WARTUNG
}
