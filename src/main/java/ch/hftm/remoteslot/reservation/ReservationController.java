package ch.hftm.remoteslot.reservation;

import ch.hftm.remoteslot.reservation.ReservationDtos.AnlegenRequest;
import ch.hftm.remoteslot.reservation.ReservationDtos.Response;
import ch.hftm.remoteslot.reservation.ReservationDtos.VerlaufseintragResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

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

    @GetMapping("/{id}")
    public Response laden(@PathVariable Long id) {
        return Response.von(service.laden(id));
    }

    @GetMapping("/{id}/verlauf")
    public List<VerlaufseintragResponse> verlauf(@PathVariable Long id) {
        return service.verlauf(id).stream().map(VerlaufseintragResponse::von).toList();
    }
}
