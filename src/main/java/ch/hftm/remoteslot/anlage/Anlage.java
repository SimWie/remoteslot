package ch.hftm.remoteslot.anlage;

import ch.hftm.remoteslot.common.KonfliktException;
import ch.hftm.remoteslot.kunde.Kunde;
import jakarta.persistence.*;

@Entity 
@Table(name = "anlage")

public class Anlage {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kunde_id", nullable = false)
    private Kunde kunde;

    @Column(nullable = false, length = 20)
    private String anlagennummer;

    @Column(nullable = false, length = 120)
    private String bezeichnung;

    @Column(nullable = false, length = 40)
    private String steuerungstyp;

    @Column(nullable = false)
    private boolean aktiv = true;

    protected Anlage() {
        // fuer JPA
    }

    public Anlage(Kunde kunde, String anlagennummer, String bezeichnung, String steuerungstyp) {
        this.kunde = kunde;
        this.anlagennummer = anlagennummer;
        this.bezeichnung = bezeichnung;
        this.steuerungstyp = steuerungstyp;
    }

    public void aendern(String bezeichnung, String steuerungstyp) {
        this.bezeichnung = bezeichnung;
        this.steuerungstyp = steuerungstyp;
    }

    public void ausserBetriebNehmen() {
        if (!aktiv) {
            throw new KonfliktException("Anlage " + this.anlagennummer + " ist bereits ausser Betrieb");
        }
        this.aktiv = false;
    }

    public Long getId() { return id; }
    public Kunde getKunde() { return kunde; }
    public String getAnlagennummer() { return anlagennummer; }
    public String getBezeichnung() { return bezeichnung; }
    public String getSteuerungstyp() { return steuerungstyp; }
    public boolean isAktiv() { return aktiv; }
}
