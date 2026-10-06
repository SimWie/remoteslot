package ch.hftm.remoteslot.reservation;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

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

    /** version = die Version, die der Client zuletzt gesehen hat (optimistisches Sperren). */
    public record StatusAendernRequest(
            @NotNull Status neuerStatus,
            @NotNull Long version,
            @Size(max = 500) String bemerkung) {
    }

    public record VerschiebenRequest(
            @NotNull OffsetDateTime beginn,
            @NotNull OffsetDateTime ende,
            @NotNull Long version) {
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

    /**
     * Eine Seite des Suchergebnisses. Bewusst ein eigenes DTO statt Springs Page-Objekt,
     * damit das JSON-Format stabil und unabhaengig von der Spring-Version bleibt.
     */
    public record Seite<T>(List<T> inhalt, int seite, int groesse, long gesamtanzahl, int seitenanzahl) {

        static <E, T> Seite<T> von(Page<E> page, Function<E, T> mapper) {
            return new Seite<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    static OffsetDateTime anzeige(Instant zeitpunkt) {
        return zeitpunkt.atZone(ANZEIGE_ZONE).toOffsetDateTime();
    }
}
