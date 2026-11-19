package cl.silbom.backend.dto;

import cl.silbom.backend.model.TipoIntervencion;
import jakarta.validation.constraints.*;

/**
 * QUÉ HACE: Valida la petición de alta de un ticket, su declaración de candado, notas y referencia
 * de falla.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: No acepta dueño, planta, estado ni contadores editables;
 * nombreTecnico es legado e ignorado para identificar al responsable. La declaración booleana no
 * demuestra el estado físico de la máquina.
 */
public record BloqueoCreateDTO(
    @NotBlank(
        message = "El identificador de la máquina es obligatorio")
    @Pattern(
        regexp = "^EQ-[A-Z]{2,4}-\\d{2}$",
        message = "El ID debe cumplir con el formato estándar EQ-XXX-00 (ej:" + " EQ-ENV-01)")
    String maquinaId,

    @NotNull(
        message = "El tipo de intervención es obligatorio (CORRECTIVA o PREVENTIVA)")
    TipoIntervencion tipoIntervencion,

    @NotBlank(
        message = "El motivo de la intervención es obligatorio")
    @Size(
        min = 5,
        max = 250,
        message = "El motivo debe tener entre 5 y 250 caracteres")
    String motivo,

    // Campo legado: el responsable real procede de la autenticación.
    @Size(
        max = 80)
    String nombreTecnico,

    @NotNull(
        message = "La confirmación del candado físico es obligatoria")
    @AssertTrue(
        message = "Debe declarar la instalación del candado físico; el software no" + " verifica aislamiento ni energía cero")
    Boolean confirmacionCandadoDs594,

    @Size(
        max = 2000,
        message = "Las notas no deben exceder 2000 caracteres")
    String notasConfidenciales,

    // Campo trampa: un cliente humano debe dejarlo vacío.
    String honeypot,

    @Size(
        max = 80)
    @Pattern(
        regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,79}",
        message = "Referencia de falla inválida")
    String referenciaFalla
) {

    /**
     * QUÉ HACE: Exige referenciaFalla para CORRECTIVA y su ausencia para PREVENTIVA.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Permite agrupar técnicos de una misma avería sin contar las
     * preventivas como fallas; JsonIgnore evita exponer esta comprobación como campo del contrato.
     */
    @AssertTrue(
            message =
                    "Una intervención CORRECTIVA requiere referenciaFalla compartida por sus"
                        + " técnicos")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isReferenciaCoherente() {
        return tipoIntervencion == cl.silbom.backend.model.TipoIntervencion.CORRECTIVA
                ? referenciaFalla != null && !referenciaFalla.isBlank()
                : referenciaFalla == null;
    }
}
