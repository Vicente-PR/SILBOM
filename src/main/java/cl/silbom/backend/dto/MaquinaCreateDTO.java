package cl.silbom.backend.dto;

import jakarta.validation.constraints.*;

/**
 * QUÉ HACE:
 * DTO inmutable (Java Record) para la creación de una nueva máquina industrial.
 *
 * POR QUÉ ESTÁ ESCRITO ASÍ:
 * - CONTROL 8 (Anti-Mass Assignment): Los registros de Java son inmutables por diseño (sin setters).
 * - CONTROL 14 (Validar inputs en la frontera): Utiliza Jakarta Validation para rechazar payloads
 *   malformados antes de que ingresen a la capa de servicio.
 * - CONTROL 12 (Protección contra bots): Incluye el campo 'honeypot'. Si un bot lo completa automáticamente,
 *   el servidor rechaza la petición.
 */
public record MaquinaCreateDTO(
    @NotBlank(message = "El identificador de la máquina es obligatorio")
    @Pattern(regexp = "^EQ-[A-Z]{2,4}-\\d{2}$", message = "El ID debe cumplir con el formato estándar EQ-XXX-00 (ej: EQ-ENV-01)")
    String id,

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
    Integer fallasCorrectivas,

    // Campo Honeypot invisible para humanos: bots automatizados suelen llenarlo
    String honeypot
) {}
