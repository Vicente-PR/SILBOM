package cl.silbom.backend.dto;

import cl.silbom.backend.model.BloqueoLoto;
import cl.silbom.backend.model.EstadoBloqueo;
import cl.silbom.backend.model.TipoIntervencion;

import java.time.LocalDateTime;

/**
 * QUÉ HACE:
 * DTO inmutable de respuesta que expone la auditoría completa de un ticket LOTO.
 *
 * POR QUÉ ESTÁ ESCRITO ASÍ:
 * - CONTROL 17 (Minimización de datos): Presenta la información limpia, con duraciones
 *   calculadas y marcas de tiempo sin exponer las tripas internas de persistencia de Hibernate.
 */
public record BloqueoResponseDTO(
    Long id,
    String maquinaId,
    TipoIntervencion tipoIntervencion,
    String motivo,
    String nombreTecnico,
    Boolean confirmacionCandadoDs594,
    EstadoBloqueo estado,
    LocalDateTime horaInicio,
    LocalDateTime horaFin,
    Long duracionMinutos,
    String notasAuditoria,
    String plantaId,
    Long tecnicoId,
    Long fallaId
) {
    /**
     * Mapper estático para transformar una entidad BloqueoLoto a su DTO de respuesta.
     */
    public static BloqueoResponseDTO fromEntity(BloqueoLoto b, String notasDescifradas) {
        return new BloqueoResponseDTO(
            b.getId(),
            b.getMaquinaId(),
            b.getTipoIntervencion(),
            b.getMotivo(),
            b.getNombreTecnico(),
            b.getConfirmacionCandadoDs594(),
            b.getEstado(),
            b.getHoraInicio(),
            b.getHoraFin(),
            b.getDuracionMinutos(),
            notasDescifradas,
            b.getPlantaId(), b.getTecnicoId(), b.getFallaId()
        );
    }
}
