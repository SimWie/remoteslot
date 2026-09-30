package ch.hftm.remoteslot.kunde;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API-DTOs, bewusst getrennt von der Entity. */
public final class KundeDtos {

    private KundeDtos() {
    }

    public record ErfassenRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 80) String ort) {
    }

    public record AendernRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 80) String ort) {
    }

    public record Response(Long id, String name, String ort) {

        static Response von(Kunde k) {
            return new Response(k.getId(), k.getName(), k.getOrt());
        }
    }
}
