package ch.hftm.remoteslot.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** API-DTOs, bewusst getrennt von der Entity. */
public final class ReservationDtos {

    /** Anzeige in Schweizer Zeit (Steckbrief, Abgrenzung). Gespeichert wird als timestamptz. */
    static final ZoneId ANZEIGE_ZONE = ZoneId.of("Europe/Zurich");

    private ReservationDtos() {
    }

    /** Zeiten mit Zeitzone, z. B. 2026-11-02T09:00:00+01:00, damit sie auch bei der Zeitumstellung eindeutig sind. */
    public record AnlegenRequest(
            @NotNull Long anlageId,
            @NotNull Long technikerId,
            @NotNull OffsetDateTime beginn,
            @NotNull OffsetDateTime ende,
            @NotNull Zweck zweck,
            @Size(max = 500) String bemerkung) {
    }

    public record Response(Long id, Long anlageId, Long technikerId, OffsetDateTime beginn, OffsetDateTime ende,
                           Zweck zweck, Status status, String bemerkung, Long version) {

        static Response von(Reservation r) {
            // getAnlage().getId() / getTechniker().getId() laden nichts nach (ID ist im Proxy bekannt).
            return new Response(r.getId(), r.getAnlage().getId(), r.getTechniker().getId(),
                    anzeige(r.getBeginn()), anzeige(r.getEnde()), r.getZweck(), r.getStatus(),
                    r.getBemerkung(), r.getVersion());
        }
    }

    public record VerlaufseintragResponse(OffsetDateTime zeitpunkt, Status alterStatus, Status neuerStatus,
                                          String bemerkung) {

        static VerlaufseintragResponse von(Statusereignis e) {
            return new VerlaufseintragResponse(anzeige(e.getZeitpunkt()), e.getAlterStatus(), e.getNeuerStatus(),
                    e.getBemerkung());
        }
    }

    static OffsetDateTime anzeige(Instant zeitpunkt) {
        return zeitpunkt.atZone(ANZEIGE_ZONE).toOffsetDateTime();
    }
}
