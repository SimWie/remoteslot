package ch.hftm.remoteslot.anlage;

import ch.hftm.remoteslot.anlage.AnlageDtos.AendernRequest;
import ch.hftm.remoteslot.anlage.AnlageDtos.ErfassenRequest;
import ch.hftm.remoteslot.anlage.AnlageDtos.Response;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/anlagen")
public class AnlageController {

    private final AnlageService service;

    public AnlageController(AnlageService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Response> erfassen(@Valid @RequestBody ErfassenRequest request, UriComponentsBuilder uri) {
        Anlage anlage = service.erfassen(request.kundeId(), request.anlagennummer(),
                request.bezeichnung(), request.steuerungstyp());
        return ResponseEntity
                .created(uri.path("/api/anlagen/{id}").buildAndExpand(anlage.getId()).toUri())
                .body(Response.von(anlage));
    }

    @GetMapping("/{id}")
    public Response laden(@PathVariable Long id) {
        return Response.von(service.laden(id));
    }

    @PutMapping("/{id}")
    public Response aendern(@PathVariable Long id, @Valid @RequestBody AendernRequest request) {
        return Response.von(service.aendern(id, request.bezeichnung(), request.steuerungstyp()));
    }

    @PostMapping("/{id}/ausser-betrieb")
    public Response ausserBetriebNehmen(@PathVariable Long id) {
        return Response.von(service.ausserBetriebNehmen(id));
    }
}
