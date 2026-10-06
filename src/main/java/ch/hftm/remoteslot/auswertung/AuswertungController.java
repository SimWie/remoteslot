package ch.hftm.remoteslot.auswertung;

import ch.hftm.remoteslot.auswertung.AuswertungDtos.AufwandKundeMonat;
import ch.hftm.remoteslot.auswertung.AuswertungDtos.AufwandTechnikerZweck;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** A7, z. B. GET /api/auswertungen/aufwand-kunde?von=2026-01-01&bis=2027-01-01 */
@RestController
@RequestMapping("/api/auswertungen")
public class AuswertungController {

    private final AuswertungService service;

    public AuswertungController(AuswertungService service) {
        this.service = service;
    }

    @GetMapping("/aufwand-kunde")
    public List<AufwandKundeMonat> aufwandJeKunde(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis) {
        return service.aufwandJeKunde(von, bis);
    }

    @GetMapping("/aufwand-techniker")
    public List<AufwandTechnikerZweck> aufwandJeTechniker(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis) {
        return service.aufwandJeTechniker(von, bis);
    }
}
