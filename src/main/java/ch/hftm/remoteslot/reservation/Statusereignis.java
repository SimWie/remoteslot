package ch.hftm.remoteslot.reservation;

import jakarta.persistence.*;

import java.time.Instant;

/** Verlaufseintrag einer Reservation. Wird nur erzeugt, nie geaendert. */
@Entity
@Table(name = "statusereignis")
public class Statusereignis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @Column(nullable = false, updatable = false)
    private Instant zeitpunkt;

    /** Leer beim Erfassen der Reservation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "alter_status", length = 20, updatable = false)
    private Status alterStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "neuer_status", nullable = false, length = 20, updatable = false)
    private Status neuerStatus;

    @Column(length = 500, updatable = false)
    private String bemerkung;

    protected Statusereignis() {
        // fuer JPA
    }

    /** Nur ueber Reservation erzeugbar, damit Status und Verlauf zusammenpassen. */
    Statusereignis(Reservation reservation, Status alterStatus, Status neuerStatus, String bemerkung) {
        this.reservation = reservation;
        this.zeitpunkt = Instant.now();
        this.alterStatus = alterStatus;
        this.neuerStatus = neuerStatus;
        this.bemerkung = bemerkung;
    }

    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public Instant getZeitpunkt() { return zeitpunkt; }
    public Status getAlterStatus() { return alterStatus; }
    public Status getNeuerStatus() { return neuerStatus; }
    public String getBemerkung() { return bemerkung; }
}
