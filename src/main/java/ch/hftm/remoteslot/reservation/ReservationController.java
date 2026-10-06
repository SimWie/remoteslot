package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.reservation.ReservationDtos.AnlegenRequest;
import ch.hftm.remoteslot.reservation.ReservationDtos.Response;
import ch.hftm.remoteslot.reservation.ReservationDtos.Seite;
import ch.hftm.remoteslot.reservation.ReservationDtos.StatusAendernRequest;
import ch.hftm.remoteslot.reservation.ReservationDtos.VerschiebenRequest;
import ch.hftm.remoteslot.reservation.ReservationDtos.VerlaufseintragResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reservationen")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Response> anlegen(@Valid @RequestBody AnlegenRequest request, UriComponentsBuilder uri) {
        Reservation reservation = service.anlegen(request.anlageId(), request.technikerId(),
                request.beginn().toInstant(), request.ende().toInstant(), request.zweck(), request.bemerkung());
        return ResponseEntity
                .created(uri.path("/api/reservationen/{id}").buildAndExpand(reservation.getId()).toUri())
                .body(Response.von(reservation));
    }

    /**
     * A6: z. B. GET /api/reservationen?kundeId=1&status=GEPLANT&von=2026-11-01T00:00:00Z&seite=0&groesse=20
     * Alle Filter sind optional. Zeitangaben im ISO-Format; ein "+" im Offset muss in der URL als %2B kodiert werden.
     */
    @GetMapping
    public Seite<Response> suchen(@RequestParam(required = false) Long kundeId,
                                  @RequestParam(required = false) Long anlageId,
                                  @RequestParam(required = false) Long technikerId,
                                  @RequestParam(required = false) Status status,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime von,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime bis,
                                  @RequestParam(defaultValue = "0") int seite,
                                  @RequestParam(defaultValue = "20") int groesse) {
        Page<Reservation> treffer = service.suchen(kundeId, anlageId, technikerId, status,
                von == null ? null : von.toInstant(), bis == null ? null : bis.toInstant(), seite, groesse);
        return Seite.von(treffer, Response::von);
    }

    @GetMapping("/{id}")
    public Response laden(@PathVariable Long id) {
        return Response.von(service.laden(id));
    }

    @PostMapping("/{id}/status")
    public Response statusAendern(@PathVariable Long id, @Valid @RequestBody StatusAendernRequest request) {
        return Response.von(service.statusAendern(id, request.version(), request.neuerStatus(), request.bemerkung()));
    }

    @PutMapping("/{id}/zeitraum")
    public Response verschieben(@PathVariable Long id, @Valid @RequestBody VerschiebenRequest request) {
        return Response.von(service.verschieben(id, request.version(),
                request.beginn().toInstant(), request.ende().toInstant()));
    }

    @GetMapping("/{id}/verlauf")
    public List<VerlaufseintragResponse> verlauf(@PathVariable Long id) {
        return service.verlauf(id).stream().map(VerlaufseintragResponse::von).toList();
    }
}
