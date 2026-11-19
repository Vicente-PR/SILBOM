package cl.silbom.backend.dto;

import cl.silbom.backend.model.EventoLoto;
import java.time.LocalDateTime;

/**
 * QUÉ HACE: Presenta una transición auditada con ID, ticket, responsable, acción y fecha.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Mantiene la respuesta separada de JPA y no incluye credenciales ni
 * notas confidenciales.
 */
public record EventoLotoResponseDTO(
    Long id,

    Long bloqueoId,

    Long tecnicoId,

    String accion,

    LocalDateTime fecha
) {

    /**
     * QUÉ HACE: Construye el DTO de un evento persistido.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La API expone la trazabilidad necesaria sin publicar detalles
     * internos de persistencia.
     */
    public static EventoLotoResponseDTO from(EventoLoto e) {
        return new EventoLotoResponseDTO(
                e.getId(), e.getBloqueoId(), e.getTecnicoId(), e.getAccion(), e.getFecha());
    }
}
