package cl.silbom.backend.service;

import cl.silbom.backend.dto.BloqueoCreateDTO;
import cl.silbom.backend.dto.BloqueoResponseDTO;
import cl.silbom.backend.dto.EventoLotoResponseDTO;
import cl.silbom.backend.exception.BotDetectionException;
import cl.silbom.backend.exception.BusinessConflictException;
import cl.silbom.backend.exception.ResourceNotFoundException;
import cl.silbom.backend.model.*;
import cl.silbom.backend.model.BloqueoLoto;
import cl.silbom.backend.model.EstadoBloqueo;
import cl.silbom.backend.model.Maquina;
import cl.silbom.backend.repository.*;
import cl.silbom.backend.repository.BloqueoLotoRepository;
import cl.silbom.backend.repository.MaquinaRepository;
import cl.silbom.backend.security.ActorActual;
import cl.silbom.backend.security.CryptoUtils;
import cl.silbom.backend.security.InputSanitizer;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QUÉ HACE: Gestiona altas, liberaciones, consultas y eventos del historial LOTO de una planta.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Combina identidad, locks pesimistas, transacciones, deduplicación de
 * fallas y cifrado de notas. Toda transición y su evento se confirman o revierten juntos.
 */
@Service
public class BloqueoService {

    private final BloqueoLotoRepository bloqueoLotoRepository;

    private final MaquinaRepository maquinaRepository;

    private final CryptoUtils cryptoUtils;

    private final String plantaId;

    private final ActorActual actor;

    private final TecnicoRepository tecnicos;

    private final FallaRepository fallas;

    private final EventoLotoRepository eventos;

    public BloqueoService(
            BloqueoLotoRepository bloqueoLotoRepository,
            MaquinaRepository maquinaRepository,
            CryptoUtils cryptoUtils,
            @Value("${silbom.security.planta-id:PLANTA-SAN-JOAQUIN}") String plantaId,
            ActorActual actor,
            TecnicoRepository tecnicos,
            FallaRepository fallas,
            EventoLotoRepository eventos) {
        this.bloqueoLotoRepository = bloqueoLotoRepository;
        this.maquinaRepository = maquinaRepository;
        this.cryptoUtils = cryptoUtils;
        this.plantaId = plantaId;
        this.actor = actor;
        this.tecnicos = tecnicos;
        this.fallas = fallas;
        this.eventos = eventos;
    }

    /**
     * QUÉ HACE: Consulta los tickets filtrados y limita la visibilidad de notas según la identidad
     * autenticada.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La pertenencia a la planta no concede por sí sola permiso para
     * leer notas confidenciales.
     */
    @Transactional(readOnly = true)
    public List<BloqueoResponseDTO> listarBloqueos(
            String maquinaId, EstadoBloqueo estado, boolean incluirNotas) {
        actor.obtener();
        return bloqueoLotoRepository
                .findBloqueosFiltrados(this.plantaId, maquinaId, estado)
                .stream()
                .map(b -> BloqueoResponseDTO.fromEntity(b, notasVisibles(b, incluirNotas)))
                .toList();
    }

    /**
     * QUÉ HACE: Busca un ticket dentro de la planta y lo convierte a DTO.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Evita exponer la entidad y aplica permisos antes de descifrar
     * notas.
     */
    @Transactional(readOnly = true)
    public BloqueoResponseDTO obtenerPorId(Long id, boolean incluirNotas) {
        BloqueoLoto bloqueo =
                bloqueoLotoRepository
                        .findByIdAndPlantaId(id, this.plantaId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Ticket LOTO #" + id + " no encontrado"));
        return BloqueoResponseDTO.fromEntity(bloqueo, notasVisibles(bloqueo, incluirNotas));
    }

    /**
     * QUÉ HACE: Descifra notas solo para el responsable o un supervisor si se solicitaron.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El contenido cifrado en reposo también necesita autorización al
     * recuperarlo (Controles 5, 7 y 17).
     */
    private String notasVisibles(BloqueoLoto bloqueo, boolean incluirNotas) {
        var actual = actor.obtener();
        boolean autorizado =
                actual.rol() == Rol.SUPERVISOR || actual.id().equals(bloqueo.getTecnicoId());
        return incluirNotas && autorizado
                ? cryptoUtils.decrypt(bloqueo.getNotasAuditoriaCifradas())
                : null;
    }

    /**
     * QUÉ HACE: Valida al responsable y la declaración de candado, agrupa la falla y crea ticket y
     * evento.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Normaliza motivo y notas de texto antes de persistir; las notas
     * se cifran con AES-GCM y la respuesta devuelve el mismo texto normalizado. Esta limpieza no
     * sustituye el escape contextual del frontend ni prueba aislamiento físico.
     */
    @Transactional
    public BloqueoResponseDTO crearBloqueo(BloqueoCreateDTO dto) {
        var responsable =
                tecnicos.findForUpdate(actor.obtener().id(), plantaId)
                        .orElseThrow(
                                () ->
                                        new cl.silbom.backend.exception.ForbiddenOperationException(
                                                "Técnico inactivo o de otra planta"));
        if (!dto.isReferenciaCoherente()) {
            throw new IllegalArgumentException(
                    "CORRECTIVA requiere referenciaFalla; PREVENTIVA no la admite");
        }
        if (dto.notasConfidenciales() != null && dto.notasConfidenciales().length() > 2000) {
            throw new IllegalArgumentException("Notas demasiado extensas");
        }
        if (dto.honeypot() != null && !dto.honeypot().isBlank()) {
            throw new BotDetectionException("Actividad automatizada detectada vía honeypot.");
        }
        if (dto.confirmacionCandadoDs594() == null || !dto.confirmacionCandadoDs594()) {
            throw new IllegalArgumentException(
                    "Debe confirmar haber colocado el candado físico conforme al DS 594.");
        }
        Maquina maquina =
                maquinaRepository
                        .findForUpdate(dto.maquinaId(), this.plantaId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Máquina '" + dto.maquinaId() + "' no encontrada"));
        String motivoSanitizado = InputSanitizer.required(dto.motivo(), 5, 250);
        String tecnicoSanitizado = responsable.getNombre();
        boolean nuevaFalla = false;
        Long fallaId = null;
        if (dto.tipoIntervencion() == TipoIntervencion.CORRECTIVA) {
            if (!dto.referenciaFalla().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
                throw new IllegalArgumentException("Referencia de falla inválida");
            }
            var existente =
                    fallas.findByMaquinaIdAndPlantaIdAndReferencia(
                            maquina.getId(), plantaId, dto.referenciaFalla());
            nuevaFalla = existente.isEmpty();
            fallaId =
                    existente
                            .orElseGet(
                                    () ->
                                            fallas.save(
                                                    new Falla(
                                                            maquina.getId(),
                                                            plantaId,
                                                            dto.referenciaFalla())))
                            .getId();
        }
        BloqueoLoto nuevoBloqueo =
                new BloqueoLoto(
                        maquina.getId(),
                        dto.tipoIntervencion(),
                        motivoSanitizado,
                        tecnicoSanitizado,
                        true,
                        this.plantaId);
        // Normalizar antes de cifrar y responder: ambas rutas deben usar el mismo texto.
        String notasSanitizadas = null;
        if (dto.notasConfidenciales() != null && !dto.notasConfidenciales().isBlank()) {
            notasSanitizadas = InputSanitizer.sanitize(dto.notasConfidenciales());
            if (notasSanitizadas.isBlank()) {
                notasSanitizadas = null;
            }
        }
        if (notasSanitizadas != null) {
            nuevoBloqueo.setNotasAuditoriaCifradas(cryptoUtils.encrypt(notasSanitizadas));
        }
        nuevoBloqueo.asignarResponsable(responsable.getId(), fallaId);
        // Aplicar regla de dominio sobre la máquina (Multi-LOTO y MTBF)
        maquina.registrarEntradaBloqueo(nuevaFalla);
        maquinaRepository.save(maquina);
        BloqueoLoto guardado = bloqueoLotoRepository.save(nuevoBloqueo);
        eventos.save(new EventoLoto(guardado.getId(), responsable.getId(), "CREADO", plantaId));
        return BloqueoResponseDTO.fromEntity(guardado, notasSanitizadas);
    }

    /**
     * QUÉ HACE: Libera un ticket del responsable, actualiza el contador y agrega su evento de
     * auditoría.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El orden global de locks es Técnico → Máquina → Ticket.
     * findMaquinaId consulta una proyección antes de cargar el ticket, para no conservar una
     * entidad desactualizada mientras se espera el lock. El orden común reduce interbloqueos y,
     * junto a la validación de estado, evita descontar dos veces.
     */
    @Transactional
    public BloqueoResponseDTO liberarBloqueo(Long id) {
        // Orden global: Técnico → Máquina → Ticket. Mantenerlo en las escrituras LOTO.
        var responsable =
                tecnicos.findForUpdate(actor.obtener().id(), plantaId)
                        .orElseThrow(
                                () ->
                                        new cl.silbom.backend.exception.ForbiddenOperationException(
                                                "Técnico inactivo"));
        // Consultar solo el ID evita cargar un ticket que pueda quedar obsoleto al esperar.
        String maquinaId =
                bloqueoLotoRepository
                        .findMaquinaId(id, plantaId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Ticket LOTO no encontrado"));
        // Siempre máquina antes de ticket: evita contadores perdidos y cierres dobles.
        Maquina maquina =
                maquinaRepository
                        .findForUpdate(maquinaId, plantaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Máquina no encontrada"));
        BloqueoLoto bloqueo =
                bloqueoLotoRepository
                        .findForUpdate(id, plantaId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Ticket LOTO no encontrado"));
        actor.exigirPropietario(bloqueo.getTecnicoId());
        if (bloqueo.getEstado() == EstadoBloqueo.CERRADO) {
            throw new BusinessConflictException("El ticket LOTO ya fue liberado");
        }
        bloqueo.cerrarTicket();
        maquina.registrarSalidaBloqueo();
        maquinaRepository.save(maquina);
        BloqueoLoto actualizado = bloqueoLotoRepository.save(bloqueo);
        eventos.save(new EventoLoto(id, responsable.getId(), "LIBERADO", plantaId));
        return BloqueoResponseDTO.fromEntity(
                actualizado, cryptoUtils.decrypt(actualizado.getNotasAuditoriaCifradas()));
    }

    /**
     * QUÉ HACE: Rechaza la eliminación de tickets existentes, activos o cerrados, con 409.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El historial de seguridad debe conservarse; no se borra para
     * satisfacer un CRUD académico.
     */
    @Transactional
    public void eliminarBloqueo(Long id) {
        BloqueoLoto bloqueo =
                bloqueoLotoRepository
                        .findByIdAndPlantaId(id, this.plantaId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Ticket LOTO #"
                                                        + id
                                                        + " no encontrado para eliminar"));
        throw new BusinessConflictException(
                "El historial LOTO es inmutable: no se eliminan tickets activos ni CERRADOS");
    }

    /**
     * QUÉ HACE: Consulta el historial de un ticket de la planta sin incluir sus notas.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Mantiene la trazabilidad separada del contenido confidencial.
     */
    @Transactional(readOnly = true)
    public List<EventoLotoResponseDTO> eventos(Long id) {
        obtenerPorId(id, false);
        return eventos.findByBloqueoIdAndPlantaIdOrderById(id, plantaId).stream()
                .map(EventoLotoResponseDTO::from)
                .toList();
    }
}
