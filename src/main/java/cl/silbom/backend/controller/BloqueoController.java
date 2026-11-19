package cl.silbom.backend.controller;

import cl.silbom.backend.dto.BloqueoCreateDTO;
import cl.silbom.backend.dto.BloqueoResponseDTO;
import cl.silbom.backend.model.EstadoBloqueo;
import cl.silbom.backend.service.BloqueoService;
import cl.silbom.backend.dto.EventoLotoResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * QUÉ HACE:
 * Controlador REST para la gestión de tickets LOTO (/api/v1/bloqueos).
 *
 * POR QUÉ ESTÁ ESCRITO ASÍ:
 * - Provee los endpoints para creación de bloqueos, consulta histórica, liberación y eliminación.
 * - Activa la validación estricta de bean validation (@Valid) en la frontera web.
 * - Devuelve los códigos HTTP estándar REST: 200 OK, 201 Created, 204 No Content.
 */
@RestController
@RequestMapping("/api/v1/bloqueos")
public class BloqueoController {

    private final BloqueoService bloqueoService;

    public BloqueoController(BloqueoService bloqueoService) {
        this.bloqueoService = bloqueoService;
    }

    /**
     * GET /api/v1/bloqueos: Consulta el historial de bloqueos con filtros opcionales (?maquinaId=...&estado=...).
     */
    @GetMapping
    public ResponseEntity<List<BloqueoResponseDTO>> listar(
        @RequestParam(required = false) String maquinaId,
        @RequestParam(required = false) EstadoBloqueo estado,
        HttpServletRequest request
    ) {
        List<BloqueoResponseDTO> lista = bloqueoService.listarBloqueos(maquinaId, estado, true);
        return ResponseEntity.ok(lista);
    }

    /**
     * GET /api/v1/bloqueos/{id}: Consulta un ticket específico por su identificador.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BloqueoResponseDTO> obtenerPorId(@PathVariable Long id, HttpServletRequest request) {
        BloqueoResponseDTO bloqueo = bloqueoService.obtenerPorId(id, true);
        return ResponseEntity.ok(bloqueo);
    }

    /**
     * POST /api/v1/bloqueos: Abre un nuevo bloqueo LOTO (Energía Cero y Multi-LOTO).
     */
    @PostMapping
    public ResponseEntity<BloqueoResponseDTO> crear(@Valid @RequestBody BloqueoCreateDTO dto) {
        BloqueoResponseDTO creado = bloqueoService.crearBloqueo(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * PUT /api/v1/bloqueos/{id}/liberar: Cierra el ticket de bloqueo y actualiza el estado Multi-LOTO de la máquina.
     */
    @PutMapping("/{id}/liberar")
    public ResponseEntity<BloqueoResponseDTO> liberar(@PathVariable Long id) {
        BloqueoResponseDTO liberado = bloqueoService.liberarBloqueo(id);
        return ResponseEntity.ok(liberado);
    }

    /**
     * DELETE /api/v1/bloqueos/{id}: Elimina un ticket del historial (solo si está en estado CERRADO).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        bloqueoService.eliminarBloqueo(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/eventos")
    public ResponseEntity<List<EventoLotoResponseDTO>> eventos(@PathVariable Long id) {
        return ResponseEntity.ok(bloqueoService.eventos(id));
    }
}
