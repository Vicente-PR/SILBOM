package cl.silbom.backend.dto;

import cl.silbom.backend.model.Rol;
import jakarta.validation.constraints.*;

/**
 * QUÉ HACE: Define nombre, rol y contraseña opcional para actualizar una cuenta.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El servicio controla qué supervisor puede aplicarlo. toString
 * devuelve una representación redactada y no expone la contraseña original.
 */
public record TecnicoUpdateDTO(
    @NotBlank
    @Size(
        min = 3,
        max = 80)
    String nombre,

    @NotNull
    Rol rol,

    @Size(
        min = 12,
        max = 72)
    String password
) {

    /**
     * QUÉ HACE: Devuelve TecnicoUpdateDTO[redacted] sin los valores recibidos.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Evita que el registro accidental del DTO revele una contraseña
     * en texto plano.
     */
    @Override
    public String toString() {
        return "TecnicoUpdateDTO[redacted]";
    }
}
