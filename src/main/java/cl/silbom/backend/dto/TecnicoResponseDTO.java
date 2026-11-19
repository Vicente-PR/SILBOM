package cl.silbom.backend.dto;

import cl.silbom.backend.model.*;

/**
 * QUÉ HACE: Representa los campos públicos de una cuenta para las respuestas administrativas.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Excluye passwordHash y contraseña; el cliente recibe identidad,
 * nombre, rol y estado, no la entidad completa.
 */
public record TecnicoResponseDTO(
    Long id,

    String username,

    String nombre,

    Rol rol,

    boolean activo
) {

    /**
     * QUÉ HACE: Convierte una entidad Tecnico a su contrato de salida.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La selección explícita de campos aplica minimización de datos
     * (Control 17).
     */
    public static TecnicoResponseDTO from(Tecnico t) {
        return new TecnicoResponseDTO(
                t.getId(), t.getUsername(), t.getNombre(), t.getRol(), t.isActivo());
    }
}
