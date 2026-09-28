package ch.hftm.remoteslot.techniker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API-DTOs, bewusst getrennt von der Entity. */
public final class TechnikerDtos {

    private TechnikerDtos() {
    }

    public record ErfassenRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{2,6}$", message = "2 bis 6 Grossbuchstaben") String kuerzel,
            @NotBlank @Size(max = 60) String vorname,
            @NotBlank @Size(max = 60) String nachname) {
    }

    public record AendernRequest(
            @NotBlank @Size(max = 60) String vorname,
            @NotBlank @Size(max = 60) String nachname) {
    }

    public record Response(Long id, String kuerzel, String vorname, String nachname, boolean aktiv) {

        static Response von(Techniker t) {
            return new Response(t.getId(), t.getKuerzel(), t.getVorname(), t.getNachname(), t.isAktiv());
        }
    }
}
