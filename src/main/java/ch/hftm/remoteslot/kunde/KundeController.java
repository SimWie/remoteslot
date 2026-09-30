package ch.hftm.remoteslot.kunde;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import ch.hftm.remoteslot.kunde.KundeDtos.*;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/kunden")
public class KundeController {
    // Service per Konstruktor
    private final KundeService service;

    KundeController(KundeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Response> erfassen(@Valid @RequestBody ErfassenRequest request, UriComponentsBuilder uri) {
        Kunde kunde = service.erfassen(request.name(), request.ort());
        return ResponseEntity
                .created(uri.path("/api/kunden/{id}").buildAndExpand(kunde.getId()).toUri())
                .body(Response.von(kunde));
    }

    @GetMapping("/{id}")
    public Response laden(@PathVariable Long id) {
        return Response.von(service.laden(id));
    }

    @PutMapping("/{id}")
    public Response aendern(@PathVariable Long id, @Valid @RequestBody AendernRequest request) {
        return Response.von(service.aendern(id, request.name(), request.ort()));
    }
}