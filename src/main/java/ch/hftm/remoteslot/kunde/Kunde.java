package ch.hftm.remoteslot.kunde;

import jakarta.persistence.*;

@Entity 
@Table(name = "kunde")

public class Kunde {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String ort;

    protected Kunde() {
        // fuer JPA
    }

    public Kunde(String name, String ort) {
        this.name = name;
        this.ort = ort;
    }

    public void aendern(String name, String ort) {
        this.name = name;
        this.ort = ort;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getOrt() { return ort; }
}
