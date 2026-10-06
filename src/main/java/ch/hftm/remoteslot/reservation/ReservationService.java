package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.anlage.Anlage;
import ch.hftm.remoteslot.anlage.AnlageRepository;
import ch.hftm.remoteslot.common.KonfliktException;
import ch.hftm.remoteslot.common.NichtGefundenException;
import ch.hftm.remoteslot.common.UngueltigeAnfrageException;
import ch.hftm.remoteslot.techniker.Techniker;
import ch.hftm.remoteslot.techniker.TechnikerRepository;
import org.springframework.lang.NonNull;
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
