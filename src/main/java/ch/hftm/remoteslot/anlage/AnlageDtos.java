package ch.hftm.remoteslot.anlage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API-DTOs, bewusst getrennt von der Entity. */
public final class AnlageDtos {

    private AnlageDtos() {
    }

    public record ErfassenRequest(
            @NotNull Long kundeId,
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,20}$", message = "3 bis 20 Zeichen: Grossbuchstaben, Ziffern, Bindestrich") String anlagennummer,
            @NotBlank @Size(max = 120) String bezeichnung,
            @NotBlank @Size(max = 40) String steuerungstyp) {
    }

    /** Kunde und Anlagennummer sind bewusst nicht aenderbar. */
    public record AendernRequest(
            @NotBlank @Size(max = 120) String bezeichnung,
            @NotBlank @Size(max = 40) String steuerungstyp) {
    }

    public record Response(Long id, Long kundeId, String anlagennummer, String bezeichnung,
                           String steuerungstyp, boolean aktiv) {

        static Response von(Anlage a) {
            // getKunde().getId() laedt den Kunden nicht nach (LAZY): die ID ist im Proxy bekannt.
            return new Response(a.getId(), a.getKunde().getId(), a.getAnlagennummer(), a.getBezeichnung(),
                    a.getSteuerungstyp(), a.isAktiv());
        }
    }
}
