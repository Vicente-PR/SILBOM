package cl.silbom.backend.service;

import cl.silbom.backend.dto.MaquinaCreateDTO;
import cl.silbom.backend.dto.MaquinaResponseDTO;
import cl.silbom.backend.dto.MaquinaUpdateDTO;
import cl.silbom.backend.exception.BotDetectionException;
import cl.silbom.backend.exception.BusinessConflictException;
import cl.silbom.backend.exception.ResourceAlreadyExistsException;
import cl.silbom.backend.exception.ResourceNotFoundException;
import cl.silbom.backend.model.EstadoBloqueo;
import cl.silbom.backend.model.Maquina;
import cl.silbom.backend.repository.BloqueoLotoRepository;
import cl.silbom.backend.repository.MaquinaRepository;
import cl.silbom.backend.security.InputSanitizer;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QUÉ HACE: Gestiona el catálogo, las mediciones declaradas y la baja lógica de máquinas.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Las lecturas usan DTO y filtro de planta. Las mutaciones requieren
 * supervisor y mantienen locks de máquina para coordinarse con los tickets LOTO.
 */
@Service
public class MaquinaService {

    private final MaquinaRepository maquinaRepository;

    private final BloqueoLotoRepository bloqueoLotoRepository;

    private final String plantaId;

    private final cl.silbom.backend.security.ActorActual actor;

    public MaquinaService(
            MaquinaRepository maquinaRepository,
            BloqueoLotoRepository bloqueoLotoRepository,
            @Value("${silbom.security.planta-id:PLANTA-SAN-JOAQUIN}") String plantaId,
            cl.silbom.backend.security.ActorActual actor) {
        this.maquinaRepository = maquinaRepository;
        this.bloqueoLotoRepository = bloqueoLotoRepository;
        this.plantaId = plantaId;
        this.actor = actor;
    }

    /** Lista todas las máquinas de la planta con filtro opcional de bloqueo. */
    @Transactional(readOnly = true)
    public List<MaquinaResponseDTO> listarMaquinas(Boolean bloqueada) {
        return maquinaRepository.findMaquinasFiltradas(this.plantaId, bloqueada).stream()
                .map(MaquinaResponseDTO::fromEntity)
                .toList();
    }

    /** Obtiene una máquina por su identificador único dentro de la planta autorizada. */
    @Transactional(readOnly = true)
    public MaquinaResponseDTO obtenerPorId(String id) {
        Maquina maquina =
                maquinaRepository
                        .findByIdAndPlantaIdAndActivaTrue(id, this.plantaId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Máquina con ID '"
                                                        + id
                                                        + "' no encontrada en "
                                                        + this.plantaId));
        return MaquinaResponseDTO.fromEntity(maquina);
    }

    /**
     * QUÉ HACE: Normaliza los campos admitidos y crea un equipo con ID no utilizado.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Los estados de bloqueo y la planta son responsabilidad del
     * servidor, no campos de asignación masiva.
     */
    @Transactional
    public MaquinaResponseDTO crearMaquina(MaquinaCreateDTO dto) {
        actor.exigirSupervisor();
        // Control 12: Detección de bots por trampa honeypot
        if (dto.honeypot() != null && !dto.honeypot().isBlank()) {
            throw new BotDetectionException("Actividad automatizada detectada vía honeypot.");
        }
        // Validación de existencia previa
        if (maquinaRepository.existsByIdAndPlantaId(dto.id(), this.plantaId)) {
            throw new ResourceAlreadyExistsException(
                    "La máquina con ID '" + dto.id() + "' ya existe en el sistema.");
        }
        // Control 15: Sanitización de entradas de texto libre
        String nombreSanitizado = InputSanitizer.required(dto.nombre(), 3, 100);
        String areaSanitizada = InputSanitizer.required(dto.area(), 3, 100);
        String iconoSanitizado = InputSanitizer.required(dto.icono(), 1, 10);
        Maquina nuevaMaquina =
                new Maquina(
                        dto.id(),
                        nombreSanitizado,
                        areaSanitizada,
                        iconoSanitizado,
                        dto.horasOperativas(),
                        dto.fallasCorrectivas(),
                        this.plantaId);
        Maquina guardada = maquinaRepository.save(nuevaMaquina);
        return MaquinaResponseDTO.fromEntity(guardada);
    }

    /**
     * QUÉ HACE: Exige supervisor, bloquea la máquina y actualiza las mediciones permitidas.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El lock coordina estas escrituras con las operaciones de
     * Multi-LOTO.
     */
    @Transactional
    public MaquinaResponseDTO actualizarMaquina(String id, MaquinaUpdateDTO dto) {
        actor.exigirSupervisor();
        Maquina maquina =
                maquinaRepository
                        .findForUpdate(id, this.plantaId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Máquina con ID '"
                                                        + id
                                                        + "' no encontrada para actualizar"));
        String nombreSanitizado = InputSanitizer.required(dto.nombre(), 3, 100);
        String areaSanitizada = InputSanitizer.required(dto.area(), 3, 100);
        String iconoSanitizado = InputSanitizer.required(dto.icono(), 1, 10);
        maquina.actualizarDatos(
                nombreSanitizado,
                areaSanitizada,
                iconoSanitizado,
                dto.horasOperativas(),
                dto.fallasCorrectivas());
        Maquina actualizada = maquinaRepository.save(maquina);
        return MaquinaResponseDTO.fromEntity(actualizada);
    }

    /**
     * QUÉ HACE: Comprueba los bloqueos y da de baja lógica al equipo.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: No permite retirar un equipo con tickets activos y conserva las
     * referencias históricas.
     */
    @Transactional
    public void eliminarMaquina(String id) {
        actor.exigirSupervisor();
        Maquina maquina =
                maquinaRepository
                        .findForUpdate(id, this.plantaId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Máquina con ID '"
                                                        + id
                                                        + "' no encontrada para eliminar"));
        // Regla de Seguridad Industrial LOTO: Prohibido eliminar equipos con personal trabajando
        if (Boolean.TRUE.equals(maquina.getBloqueada())
                || (maquina.getBloqueosActivos() != null && maquina.getBloqueosActivos() > 0)) {
            throw new BusinessConflictException(
                    "No se puede eliminar la máquina '"
                            + id
                            + "' porque actualmente se encuentra BLOQUEADA ("
                            + maquina.getBloqueosActivos()
                            + " técnicos interviniendo)");
        }
        long bloqueosActivos =
                bloqueoLotoRepository.countByMaquinaIdAndEstado(id, EstadoBloqueo.ACTIVO);
        if (bloqueosActivos > 0) {
            throw new BusinessConflictException(
                    "No se puede eliminar la máquina '"
                            + id
                            + "' porque tiene "
                            + bloqueosActivos
                            + " bloqueos LOTO activos");
        }
        maquina.desactivar();
    }
}
