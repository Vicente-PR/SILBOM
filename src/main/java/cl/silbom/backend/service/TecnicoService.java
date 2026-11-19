package cl.silbom.backend.service;

import cl.silbom.backend.dto.*;
import cl.silbom.backend.exception.*;
import cl.silbom.backend.model.*;
import cl.silbom.backend.repository.*;
import cl.silbom.backend.security.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QUÉ HACE: Gestiona el CRUD administrativo de técnicos, sus roles y el hash de sus contraseñas.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Requiere supervisor en cada operación, protege cambios concurrentes
 * y usa baja lógica para conservar historial. No expone entidades con hashes a los clientes.
 */
@Service
public class TecnicoService {

    private final TecnicoRepository repository;

    private final BloqueoLotoRepository bloqueos;

    private final PasswordEncoder encoder;

    private final ActorActual actor;

    private final String planta;

    public TecnicoService(
            TecnicoRepository repository,
            BloqueoLotoRepository bloqueos,
            PasswordEncoder encoder,
            ActorActual actor,
            @Value("${silbom.security.planta-id}") String planta) {
        this.repository = repository;
        this.bloqueos = bloqueos;
        this.encoder = encoder;
        this.actor = actor;
        this.planta = planta;
    }

    /**
     * QUÉ HACE: Devuelve los técnicos activos de la planta como DTO.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La identidad supervisora se valida antes de consultar datos
     * administrativos.
     */
    @Transactional(readOnly = true)
    public List<TecnicoResponseDTO> listar() {
        actor.exigirSupervisor();
        return repository.findByPlantaIdAndActivoTrueOrderById(planta).stream()
                .map(TecnicoResponseDTO::from)
                .toList();
    }

    /**
     * QUÉ HACE: Devuelve un técnico activo por ID y planta.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Una cuenta de otra planta o dada de baja no se presenta como
     * recurso disponible.
     */
    @Transactional(readOnly = true)
    public TecnicoResponseDTO obtener(Long id) {
        actor.exigirSupervisor();
        return repository
                .findByIdAndPlantaIdAndActivoTrue(id, planta)
                .map(TecnicoResponseDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Técnico no encontrado"));
    }

    /**
     * QUÉ HACE: Valida el usuario, normaliza el nombre, calcula el hash y guarda la identidad.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: No reutiliza usernames históricos, incluidos los de cuentas
     * inactivas.
     */
    @Transactional
    public TecnicoResponseDTO crear(TecnicoCreateDTO dto) {
        actor.exigirSupervisor();
        if (repository.findByUsernameAndPlantaId(dto.username(), planta).isPresent()) {
            throw new ResourceAlreadyExistsException(
                    "Nombre de usuario ya utilizado, incluso si está inactivo");
        }
        var t =
                new Tecnico(
                        dto.username(),
                        InputSanitizer.required(dto.nombre(), 3, 80),
                        hash(dto.password()),
                        dto.rol(),
                        planta);
        return TecnicoResponseDTO.from(repository.save(t));
    }

    /**
     * QUÉ HACE: Bloquea la cuenta y aplica los cambios administrativos permitidos.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Impide retirar el propio rol supervisor y conserva la contraseña
     * si no se solicita un cambio.
     */
    @Transactional
    public TecnicoResponseDTO actualizar(Long id, TecnicoUpdateDTO dto) {
        actor.exigirSupervisor();
        var t =
                repository
                        .findForUpdate(id, planta)
                        .orElseThrow(() -> new ResourceNotFoundException("Técnico no encontrado"));
        if (id.equals(actor.obtener().id()) && dto.rol() != Rol.SUPERVISOR) {
            throw new BusinessConflictException("No puede retirar su propio rol supervisor");
        }
        t.actualizar(
                InputSanitizer.required(dto.nombre(), 3, 80),
                dto.rol(),
                dto.password() == null ? null : hash(dto.password()));
        return TecnicoResponseDTO.from(t);
    }

    /**
     * QUÉ HACE: Bloquea y desactiva la cuenta si no es la propia supervisora ni tiene tickets
     * activos.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La baja lógica conserva referencias; rechazar cuentas con
     * bloqueos evita dejar tickets sin una identidad operativa.
     */
    @Transactional
    public void eliminar(Long id) {
        actor.exigirSupervisor();
        var t =
                repository
                        .findForUpdate(id, planta)
                        .orElseThrow(() -> new ResourceNotFoundException("Técnico no encontrado"));
        if (id.equals(actor.obtener().id())) {
            throw new BusinessConflictException(
                    "No puede dar de baja su propia cuenta supervisora");
        }
        if (bloqueos.existsByTecnicoIdAndEstado(id, EstadoBloqueo.ACTIVO)) {
            throw new BusinessConflictException("El técnico tiene bloqueos activos");
        }
        t.desactivar();
    }

    /**
     * QUÉ HACE: Exige al menos 12 caracteres y como máximo 72 bytes UTF-8 antes de aplicar BCrypt.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: BCrypt tiene un límite de 72 bytes; rechazar entradas superiores
     * evita truncamiento o resultados dependientes de la versión del encoder. El máximo se
     * comprueba en bytes, no solo en caracteres.
     */
    private String hash(String password) {
        if (password == null
                || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "La contraseña requiere al menos 12 caracteres y como máximo 72 bytes UTF-8");
        }
        return encoder.encode(password);
    }
}
