package cl.silbom.backend.controller;

import cl.silbom.backend.dto.*;
import cl.silbom.backend.service.TecnicoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * QUÉ HACE: Expone las rutas HTTP existentes del CRUD de técnicos bajo /api/v1/tecnicos.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Valida los DTO de entrada y delega reglas, transacciones y permisos
 * al servicio. Las altas devuelven 201, las consultas y cambios 200 y la baja lógica 204.
 */
@RestController
@RequestMapping("/api/v1/tecnicos")
public class TecnicoController {

    private final TecnicoService service;

    public TecnicoController(TecnicoService service) {
        this.service = service;
    }

    /**
     * QUÉ HACE: Atiende GET del catálogo de técnicos.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Devuelve DTO sin datos de autenticación.
     */
    @GetMapping
    public List<TecnicoResponseDTO> listar() {
        return service.listar();
    }

    /**
     * QUÉ HACE: Atiende GET por ID de técnico.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El servicio limita la consulta a la planta y al estado activo.
     */
    @GetMapping("/{id}")
    public TecnicoResponseDTO obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    /**
     * QUÉ HACE: Valida el JSON de alta y devuelve el DTO creado con 201.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El controlador no calcula hashes ni decide permisos de negocio.
     */
    @PostMapping
    public ResponseEntity<TecnicoResponseDTO> crear(@Valid @RequestBody TecnicoCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(dto));
    }

    /**
     * QUÉ HACE: Valida el JSON de actualización y delega la operación.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La autorización y el bloqueo de fila quedan en el servicio.
     */
    @PutMapping("/{id}")
    public TecnicoResponseDTO actualizar(
            @PathVariable Long id, @Valid @RequestBody TecnicoUpdateDTO dto) {
        return service.actualizar(id, dto);
    }

    /**
     * QUÉ HACE: Solicita la baja lógica y devuelve 204 cuando termina.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La conservación del historial y los conflictos de tickets se
     * resuelven en el servicio.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
