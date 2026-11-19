package cl.silbom.backend.dto;

import jakarta.validation.constraints.*;

/**
 * QUÉ HACE:
 * DTO inmutable para la actualización de datos operativos de una máquina.
 *
 * POR QUÉ ESTÁ ESCRITO ASÍ:
 * - CONTROL 8 (Anti-Mass Assignment): Solo expone los atributos modificables por un supervisor.
 * - Los campos críticos de seguridad (id, bloqueada, bloqueosActivos, plantaId) quedan
 *   estrictamente excluidos de este DTO para impedir modificaciones ilegítimas.
 */
public record MaquinaUpdateDTO(
    @NotBlank(message = "El nombre de la máquina es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    String nombre,

    @NotBlank(message = "El área física de la planta es obligatoria")
    @Size(min = 3, max = 100, message = "El área debe tener entre 3 y 100 caracteres")
    String area,

    @NotBlank(message = "El ícono visual es obligatorio")
    @Size(max = 10, message = "El ícono no debe exceder los 10 caracteres")
    String icono,

    @NotNull(message = "Las horas operativas son obligatorias")
    @PositiveOrZero(message = "Las horas operativas no pueden ser negativas")
    @DecimalMax("999999999.9")
    Double horasOperativas,

    @NotNull(message = "El número de fallas correctivas es obligatorio")
    @PositiveOrZero(message = "Las fallas correctivas no pueden ser negativas")
    Integer fallasCorrectivas
) {}
