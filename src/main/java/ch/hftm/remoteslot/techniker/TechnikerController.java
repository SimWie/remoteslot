package ch.hftm.remoteslot.techniker;

import ch.hftm.remoteslot.techniker.TechnikerDtos.AendernRequest;
import ch.hftm.remoteslot.techniker.TechnikerDtos.ErfassenRequest;
import ch.hftm.remoteslot.techniker.TechnikerDtos.Response;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/techniker")
public class TechnikerController {

    private final TechnikerService service;

    public TechnikerController(TechnikerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Response> erfassen(@Valid @RequestBody ErfassenRequest request, UriComponentsBuilder uri) {
        Techniker techniker = service.erfassen(request.kuerzel(), request.vorname(), request.nachname());
        return ResponseEntity
                .created(uri.path("/api/techniker/{id}").buildAndExpand(techniker.getId()).toUri())
                .body(Response.von(techniker));
    }

    @GetMapping("/{id}")
    public Response laden(@PathVariable Long id) {
        return Response.von(service.laden(id));
    }

    @PutMapping("/{id}")
    public Response aendern(@PathVariable Long id, @Valid @RequestBody AendernRequest request) {
        return Response.von(service.aendern(id, request.vorname(), request.nachname()));
    }

    @PostMapping("/{id}/deaktivieren")
    public Response deaktivieren(@PathVariable Long id) {
        return Response.von(service.deaktivieren(id));
    }
}
