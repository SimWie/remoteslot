package ch.hftm.remoteslot.techniker;

import ch.hftm.remoteslot.common.KonfliktException;
import jakarta.persistence.*;

@Entity
@Table(name = "techniker")
public class Techniker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 6)
    private String kuerzel;

    @Column(nullable = false, length = 60)
    private String vorname;

    @Column(nullable = false, length = 60)
    private String nachname;

    @Column(nullable = false)
    private boolean aktiv = true;

    protected Techniker() {
        // fuer JPA
    }

    public Techniker(String kuerzel, String vorname, String nachname) {
        this.kuerzel = kuerzel;
        this.vorname = vorname;
        this.nachname = nachname;
    }

    public void aendern(String vorname, String nachname) {
        this.vorname = vorname;
        this.nachname = nachname;
    }

    public void deaktivieren() {
        if (!this.aktiv) {
            throw new KonfliktException("Techniker " + this.kuerzel + " ist bereits deaktiviert.");
        }
        this.aktiv = false;
    }

    public Long getId() { return id; }
    public String getKuerzel() { return kuerzel; }
    public String getVorname() { return vorname; }
    public String getNachname() { return nachname; }
    public boolean isAktiv() { return aktiv; }
}
