package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.anlage.Anlage;
import ch.hftm.remoteslot.anlage.AnlageRepository;
import ch.hftm.remoteslot.common.KonfliktException;
import ch.hftm.remoteslot.common.NichtGefundenException;
import ch.hftm.remoteslot.common.UngueltigeAnfrageException;
import ch.hftm.remoteslot.techniker.Techniker;
import ch.hftm.remoteslot.techniker.TechnikerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    static final Duration MIN_DAUER = Duration.ofMinutes(15);
    static final Duration MAX_DAUER = Duration.ofHours(8);

    private final ReservationRepository reservationen;
    private final StatusereignisRepository statusereignisse;
    private final AnlageRepository anlagen;
    private final TechnikerRepository techniker;

    public ReservationService(ReservationRepository reservationen, StatusereignisRepository statusereignisse,
                              AnlageRepository anlagen, TechnikerRepository techniker) {
        this.reservationen = reservationen;
        this.statusereignisse = statusereignisse;
        this.anlagen = anlagen;
        this.techniker = techniker;
    }

    /**
     * A3: Reservation anlegen. Reservation und erster Verlaufseintrag werden in derselben Transaktion geschrieben.
     * Reihenfolge der Pruefungen: Eingabe (400), Existenz (404), aktiv (409).
     */
    @Transactional
    public Reservation anlegen(@NonNull Long anlageId, @NonNull Long technikerId, Instant beginn, Instant ende,
                               Zweck zweck, String bemerkung) {
        pruefeZeitraum(beginn, ende);

        // Feste Sperr-Reihenfolge: immer zuerst die Anlage, dann den Techniker (verhindert Deadlocks).
        // FOR SHARE: ein gleichzeitiges Ausser-Betrieb-Nehmen/Deaktivieren muss warten und umgekehrt.
        Anlage anlage = anlagen.findByIdZumReservieren(anlageId)
                .orElseThrow(() -> new NichtGefundenException("Anlage " + anlageId + " nicht gefunden."));
        Techniker t = techniker.findByIdZumReservieren(technikerId)
                .orElseThrow(() -> new NichtGefundenException("Techniker " + technikerId + " nicht gefunden."));

        if (!anlage.isAktiv()) {
            throw new KonfliktException("Anlage " + anlage.getAnlagennummer() + " ist ausser Betrieb und kann nicht reserviert werden.");
        }
        if (!t.isAktiv()) {
            throw new KonfliktException("Techniker " + t.getKuerzel() + " ist deaktiviert und kann nicht reserviert werden.");
        }

        // A4: Vorabpruefung nur fuer eine verstaendliche Meldung. Die eigentliche Garantie sind die
        // Ausschluss-Constraints aus V4 (zwei gleichzeitige Anfragen koennen diese Pruefung beide bestehen;
        // die zweite scheitert dann am Constraint und wird im GlobalExceptionHandler ebenfalls zu 409).
        if (reservationen.anlageBelegt(anlageId, beginn, ende)) {
            throw new KonfliktException("Anlage " + anlage.getAnlagennummer() + " ist im gewuenschten Zeitraum bereits reserviert.");
        }
        if (reservationen.technikerBelegt(technikerId, beginn, ende)) {
            throw new KonfliktException("Techniker " + t.getKuerzel() + " ist im gewuenschten Zeitraum bereits eingeplant.");
        }

        Reservation reservation = reservationen.saveAndFlush(
                new Reservation(anlage, t, beginn, ende, zweck, bemerkung));
        statusereignisse.save(reservation.erfassungsereignis());
        return reservation;
    }

    static final String BEMERKUNG_AUSSERBETRIEBNAHME = "Anlage ausser Betrieb genommen";

    /**
     * T4, Teil der Operation "Anlage ausser Betrieb nehmen" (wird vom AnlageService in dessen Transaktion aufgerufen).
     * Storniert alle GEPLANT-Reservationen der Anlage, auch ueberfaellige, und schreibt je einen Verlaufseintrag.
     * Trifft sie auf eine AKTIV-Reservation, bricht sie mit 409 ab; die Transaktion wird dann vollstaendig
     * zurueckgerollt, auch die bereits geschriebenen Stornierungen und das Deaktivieren der Anlage.
     */
    @Transactional
    public int stornierenWegenAusserbetriebnahme(Anlage anlage) {
        List<Reservation> offene = reservationen.findByAnlageIdAndStatusInOrderByBeginnAscIdAsc(
                anlage.getId(), List.of(Status.GEPLANT, Status.AKTIV));
        int storniert = 0;
        for (Reservation r : offene) {
            if (r.getStatus() == Status.AKTIV) {
                throw new KonfliktException("Anlage " + anlage.getAnlagennummer() + " hat eine laufende Reservation ("
                        + r.getId() + ") und kann nicht ausser Betrieb genommen werden.");
            }
            statusereignisse.save(r.statusWechseln(Status.STORNIERT, BEMERKUNG_AUSSERBETRIEBNAHME));
            reservationen.flush(); // bewusst sofort schreiben: ein spaeterer Fehler muss diese Aenderung zuruecknehmen
            storniert++;
        }
        return storniert;
    }

    /**
     * A5: Status aendern (inkl. Stornieren). Zulaessige Uebergaenge prueft die Entity.
     * Der Client schickt die Version mit, die er gesehen hat; ein veralteter Stand wird abgelehnt (409).
     */
    @Transactional
    public Reservation statusAendern(@NonNull Long id, @NonNull Long version, Status neuerStatus, String bemerkung) {
        Reservation reservation = laden(id);
        pruefeVersion(reservation, version);
        statusereignisse.save(reservation.statusWechseln(neuerStatus, bemerkung));
        reservationen.flush(); // erhoeht die Version, damit die Antwort den neuen Stand zeigt
        return reservation;
    }

    /** A5: Verschieben, nur im Status GEPLANT. Es gelten dieselben Regeln wie beim Anlegen (Dauer, Beginn, A4). */
    @Transactional
    public Reservation verschieben(@NonNull Long id, @NonNull Long version, Instant beginn, Instant ende) {
        pruefeZeitraum(beginn, ende);
        Reservation reservation = laden(id);
        pruefeVersion(reservation, version);
        reservation.pruefeVerschiebbar();

        // Vorabpruefung ohne die Reservation selbst; Garantie wie beim Anlegen durch die Constraints aus V4
        if (reservationen.anlageBelegtAusser(reservation.getAnlage().getId(), id, beginn, ende)) {
            throw new KonfliktException("Anlage " + reservation.getAnlage().getAnlagennummer()
                    + " ist im gewuenschten Zeitraum bereits reserviert.");
        }
        if (reservationen.technikerBelegtAusser(reservation.getTechniker().getId(), id, beginn, ende)) {
            throw new KonfliktException("Techniker " + reservation.getTechniker().getKuerzel()
                    + " ist im gewuenschten Zeitraum bereits eingeplant.");
        }

        statusereignisse.save(reservation.verschieben(beginn, ende));
        reservationen.flush();
        return reservation;
    }

    /** Optimistisches Sperren ueber die REST-Grenze: die @Version-Spalte schuetzt zusaetzlich gleichzeitige Commits. */
    private void pruefeVersion(Reservation reservation, Long version) {
        if (!reservation.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(Reservation.class, reservation.getId());
        }
    }

    static final int MAX_SEITENGROESSE = 100;

    /** Stabile Reihenfolge ueber alle Seiten: nach Beginn, bei gleichem Beginn nach ID (eindeutig). */
    static final Sort SORTIERUNG = Sort.by("beginn").ascending().and(Sort.by("id").ascending());

    /**
     * A6/T7: Suche mit optionalen Filtern, sortiert und seitenweise mit Gesamtanzahl.
     * Spring Data fuehrt dafuer zwei Abfragen aus: die Seite (LIMIT/OFFSET) und ein COUNT fuer die Gesamtanzahl.
     */
    public Page<Reservation> suchen(Long kundeId, Long anlageId, Long technikerId, Status status,
                                    Instant von, Instant bis, int seite, int groesse) {
        if (seite < 0) {
            throw new UngueltigeAnfrageException("Die Seite darf nicht negativ sein.");
        }
        if (groesse < 1 || groesse > MAX_SEITENGROESSE) {
            throw new UngueltigeAnfrageException("Die Seitengroesse muss zwischen 1 und " + MAX_SEITENGROESSE + " liegen.");
        }
        if (von != null && bis != null && !bis.isAfter(von)) {
            throw new UngueltigeAnfrageException("'bis' muss nach 'von' liegen.");
        }
        Specification<Reservation> filter = Specification.allOf(
                ReservationSpecifications.kunde(kundeId),
                ReservationSpecifications.anlage(anlageId),
                ReservationSpecifications.techniker(technikerId),
                ReservationSpecifications.status(status),
                ReservationSpecifications.abZeitpunkt(von),
                ReservationSpecifications.bisZeitpunkt(bis));
        return reservationen.findAll(filter, PageRequest.of(seite, groesse, SORTIERUNG));
    }

    public Reservation laden(@NonNull Long id) {
        return reservationen.findById(id)
                .orElseThrow(() -> new NichtGefundenException("Reservation " + id + " nicht gefunden."));
    }

    public List<Statusereignis> verlauf(@NonNull Long id) {
        laden(id); // 404, falls es die Reservation nicht gibt
        return statusereignisse.findByReservationIdOrderByZeitpunktAscIdAsc(id);
    }

    /** Regeln aus Steckbrief A3. Ende nach Beginn und Dauer prueft zusaetzlich die Datenbank (CHECK in V1). */
    private void pruefeZeitraum(Instant beginn, Instant ende) {
        if (!ende.isAfter(beginn)) {
            throw new UngueltigeAnfrageException("Das Ende muss nach dem Beginn liegen.");
        }
        Duration dauer = Duration.between(beginn, ende);
        if (dauer.compareTo(MIN_DAUER) < 0 || dauer.compareTo(MAX_DAUER) > 0) {
            throw new UngueltigeAnfrageException("Die Dauer muss zwischen 15 Minuten und 8 Stunden liegen.");
        }
        // Bewusst nur eine Anwendungsregel (siehe V1): historische Daten muessen speicherbar bleiben.
        if (beginn.isBefore(Instant.now())) {
            throw new UngueltigeAnfrageException("Der Beginn darf nicht in der Vergangenheit liegen.");
        }
    }
}
