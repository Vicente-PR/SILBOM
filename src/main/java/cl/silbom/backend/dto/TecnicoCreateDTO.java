package cl.silbom.backend.dto;

import cl.silbom.backend.model.Rol;
import jakarta.validation.constraints.*;

/**
 * QUÉ HACE: Define los datos validados para crear una identidad individual.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El record es inmutable y separa entrada de entidad. La contraseña
 * solo se usa para calcular el hash; toString la omite para prevenir filtraciones accidentales en
 * logs (Controles 1 y 17).
 */
public record TecnicoCreateDTO(
    @NotBlank
    @Pattern(
        regexp = "[a-zA-Z0-9._-]{3,50}")
    String username,

    @NotBlank
    @Size(
        min = 3,
        max = 80)
    String nombre,

    @NotBlank
    @Size(
        min = 12,
        max = 72)
    String password,

    @NotNull
    Rol rol
) {

    /**
     * QUÉ HACE: Representa el DTO mostrando únicamente el username.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La representación automática de un record incluiría la
     * contraseña original; no debe utilizarse en logs.
     */
    @Override
    public String toString() {
        return "TecnicoCreateDTO[" + username + "]";
    }
}
