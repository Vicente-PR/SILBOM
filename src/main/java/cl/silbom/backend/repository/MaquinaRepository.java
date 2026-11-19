package cl.silbom.backend.repository;

import cl.silbom.backend.model.Maquina;
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
 * Repositorio Spring Data JPA para la entidad Maquina.
 *
 * POR QUÉ ESTÁ ESCRITO ASÍ:
 * - CONTROL 13 (Prevención de SQL Injection): Todas las consultas están 100% parametrizadas
 *   mediante JPQL y parámetros enlazados con @Param(:plantaId, :bloqueada).
 * - CONTROLES 4 y 7 (Row-Level Security y Anti-IDOR en la capa de datos):
 *   Cada búsqueda filtra obligatoriamente por 'plantaId' para aislar estrictamente los datos.
 */
@Repository
public interface MaquinaRepository extends JpaRepository<Maquina, String> {

    /**
     * Búsqueda segura por ID y planta autorizada (Anti-IDOR).
     */
    Optional<Maquina> findByIdAndPlantaIdAndActivaTrue(String id, String plantaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Maquina m where m.id=:id and m.plantaId=:planta and m.activa=true")
    Optional<Maquina> findForUpdate(@Param("id") String id, @Param("planta") String planta);

    /**
     * Comprueba existencia dentro de la planta autorizada.
     */
    boolean existsByIdAndPlantaId(String id, String plantaId);

    /**
     * Lista máquinas con filtro opcional de estado 'bloqueada'.
     * Cero concatenación de cadenas SQL.
     */
    @Query("SELECT m FROM Maquina m WHERE m.plantaId = :plantaId AND m.activa=true " +
           "AND (:bloqueada IS NULL OR m.bloqueada = :bloqueada) " +
           "ORDER BY m.id ASC")
    List<Maquina> findMaquinasFiltradas(
        @Param("plantaId") String plantaId,
        @Param("bloqueada") Boolean bloqueada
    );
}
