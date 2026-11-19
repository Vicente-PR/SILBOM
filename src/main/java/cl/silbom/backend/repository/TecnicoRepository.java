package cl.silbom.backend.repository;

import cl.silbom.backend.model.Tecnico;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * QUÉ HACE: Consulta técnicos por planta, identidad y estado; permite bloquear una fila antes de
 * modificarla.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Las consultas derivadas y JPQL usan parámetros. PESSIMISTIC_WRITE
 * equivale al bloqueo de fila SELECT FOR UPDATE de PostgreSQL y serializa las operaciones sobre esa
 * identidad.
 */
public interface TecnicoRepository extends JpaRepository<Tecnico, Long> {

    Optional<Tecnico> findByIdAndPlantaIdAndActivoTrue(Long id, String plantaId);

    Optional<Tecnico> findByUsernameAndPlantaId(String username, String plantaId);

    List<Tecnico> findByPlantaIdAndActivoTrueOrderById(String plantaId);

    /**
     * QUÉ HACE: Obtiene y bloquea el técnico activo de la planta.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Es el primer lock del orden Técnico → Máquina → Ticket usado por
     * el servicio LOTO; también coordina cambios y bajas de cuentas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tecnico t where t.id=:id and t.plantaId=:planta and t.activo=true")
    Optional<Tecnico> findForUpdate(@Param("id") Long id, @Param("planta") String planta);
}
