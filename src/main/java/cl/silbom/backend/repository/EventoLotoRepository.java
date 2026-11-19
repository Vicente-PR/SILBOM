package cl.silbom.backend.repository;

import cl.silbom.backend.model.EventoLoto;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * QUÉ HACE: Expone únicamente inserción de eventos y consulta del historial por ticket y planta.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Se extiende Repository en lugar de JpaRepository para no ofrecer
 * métodos delete en esta interfaz. La protección completa se apoya además en la entidad y en los
 * triggers y permisos SQL.
 */
public interface EventoLotoRepository extends Repository<EventoLoto, Long> {

    /**
     * QUÉ HACE: Persiste el evento de una transición dentro de la transacción del ticket.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El modelo de uso solo agrega eventos; esta firma por sí sola no
     * reemplaza las restricciones de base de datos.
     */
    EventoLoto save(EventoLoto evento);

    /**
     * QUÉ HACE: Devuelve los eventos de un ticket de la planta en orden de ID.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Los filtros de pertenencia y RLS evitan mezclar historial de
     * plantas diferentes.
     */
    List<EventoLoto> findByBloqueoIdAndPlantaIdOrderById(Long bloqueoId, String plantaId);
}
