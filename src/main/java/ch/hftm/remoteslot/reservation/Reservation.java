package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.anlage.Anlage;
import ch.hftm.remoteslot.common.KonfliktException;
import ch.hftm.remoteslot.techniker.Techniker;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anlage_id", nullable = false)
    private Anlage anlage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "techniker_id", nullable = false)
    private Techniker techniker;

    @Column(nullable = false)
    private Instant beginn;

    @Column(nullable = false)
    private Instant ende;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Zweck zweck;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.GEPLANT;

    @Column(length = 500)
    private String bemerkung;

    /** Optimistisches Sperren (T5): jede Aenderung erhoeht die Version, ein veralteter Stand wird abgelehnt. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "erstellt_am", nullable = false, updatable = false)
    private Instant erstelltAm;

    protected Reservation() {
        // fuer JPA
    }

    /** Eine neue Reservation ist immer GEPLANT (Steckbrief A3). */
    public Reservation(Anlage anlage, Techniker techniker, Instant beginn, Instant ende, Zweck zweck, String bemerkung) {
        this.anlage = anlage;
        this.techniker = techniker;
        this.beginn = beginn;
        this.ende = ende;
        this.zweck = zweck;
        this.bemerkung = bemerkung;
        this.erstelltAm = Instant.now();
    }

    /** Erster Verlaufseintrag beim Erfassen (alter Status leer). Wird vom Service in derselben Transaktion gespeichert. */
    public Statusereignis erfassungsereignis() {
        return new Statusereignis(this, null, status, null);
    }

    /**
     * Wechselt den Status und liefert den passenden Verlaufseintrag.
     * Der Service speichert beides in derselben Transaktion (Konsistenz Status und Verlauf).
     */
    public Statusereignis statusWechseln(Status neu, String bemerkung) {
        if (!status.darfWechselnZu(neu)) {
            throw new KonfliktException("Statuswechsel von " + status + " zu " + neu + " ist nicht zulaessig.");
        }
        Status alt = status;
        this.status = neu;
        return new Statusereignis(this, alt, neu, bemerkung);
    }

    public Long getId() { return id; }
    public Anlage getAnlage() { return anlage; }
    public Techniker getTechniker() { return techniker; }
    public Instant getBeginn() { return beginn; }
    public Instant getEnde() { return ende; }
    public Zweck getZweck() { return zweck; }
    public Status getStatus() { return status; }
    public String getBemerkung() { return bemerkung; }
    public Long getVersion() { return version; }
    public Instant getErstelltAm() { return erstelltAm; }
}
