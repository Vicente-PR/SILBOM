package cl.silbom.backend.repository;

import cl.silbom.backend.model.Falla;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * QUÉ HACE: Busca y persiste referencias de fallas por máquina y planta.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El método parametrizado permite reutilizar una avería existente. No
 * añade un lock de otro orden: el servicio ya mantiene bloqueada la máquina y la base aplica la
 * unicidad de la referencia.
 */
public interface FallaRepository extends JpaRepository<Falla, Long> {

    Optional<Falla> findByMaquinaIdAndPlantaIdAndReferencia(
            String maquinaId, String plantaId, String referencia);
}
