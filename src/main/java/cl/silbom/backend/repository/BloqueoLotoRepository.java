package cl.silbom.backend.repository;

import cl.silbom.backend.model.BloqueoLoto;
import cl.silbom.backend.model.EstadoBloqueo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

/**
 * QUÉ HACE:
 * Repositorio Spring Data JPA para la entidad BloqueoLoto.
 *
 * POR QUÉ ESTÁ ESCRITO ASÍ:
 * - CONTROL 13 (Parametrización estricta de consultas SQL/JPQL):
 *   Las consultas filtran por máquina y estado utilizando parámetros enlazados :maquinaId y :estado.
 * - CONTROLES 4 y 7 (Anti-IDOR y aislamiento de planta):
 *   No permite acceder a tickets que pertenezcan a otra planta industrial.
 */
@Repository
public interface BloqueoLotoRepository extends JpaRepository<BloqueoLoto, Long> {

    /**
     * Búsqueda por ID asegurando pertenencia a la planta autorizada.
     */
    Optional<BloqueoLoto> findByIdAndPlantaId(Long id, String plantaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BloqueoLoto b where b.id=:id and b.plantaId=:planta")
    Optional<BloqueoLoto> findForUpdate(@Param("id") Long id, @Param("planta") String planta);

    boolean existsByTecnicoIdAndEstado(Long tecnicoId, EstadoBloqueo estado);

    @Query("select b.maquinaId from BloqueoLoto b where b.id=:id and b.plantaId=:planta")
    Optional<String> findMaquinaId(@Param("id") Long id, @Param("planta") String planta);

    /**
     * Lista tickets con filtros dinámicos opcionales por máquina y estado,
     * ordenados del más reciente al más antiguo.
     */
    @Query("SELECT b FROM BloqueoLoto b WHERE b.plantaId = :plantaId " +
           "AND (:maquinaId IS NULL OR b.maquinaId = :maquinaId) " +
           "AND (:estado IS NULL OR b.estado = :estado) " +
           "ORDER BY b.horaInicio DESC, b.id DESC")
    List<BloqueoLoto> findBloqueosFiltrados(
        @Param("plantaId") String plantaId,
        @Param("maquinaId") String maquinaId,
        @Param("estado") EstadoBloqueo estado
    );

    /**
     * Cuenta cuántos bloqueos activos existen actualmente para una máquina.
     */
    long countByMaquinaIdAndEstado(String maquinaId, EstadoBloqueo estado);
}
