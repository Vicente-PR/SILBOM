package cl.silbom.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * QUÉ HACE: Registra CREADO, LIBERADO o LEGADO_IMPORTADO con ticket, responsable y fecha.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Es un historial append-only sin setters, con columnas
 * updatable=false. Estas medidas de JPA se complementan con privilegios y triggers PostgreSQL: las
 * anotaciones por sí solas no garantizan inmutabilidad frente a SQL directo. No contiene
 * contraseñas ni notas confidenciales.
 */
@Entity
@Table(name = "eventos_loto")
public class EventoLoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bloqueo_id", nullable = false, updatable = false)
    private Long bloqueoId;

    @Column(name = "tecnico_id", updatable = false)
    private Long tecnicoId;

    @Column(nullable = false, length = 30, updatable = false)
    private String accion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(name = "planta_id", nullable = false, length = 50, updatable = false)
    private String plantaId;

    protected EventoLoto() {}

    public EventoLoto(Long bloqueoId, Long tecnicoId, String accion, String plantaId) {
        this.bloqueoId = bloqueoId;
        this.tecnicoId = tecnicoId;
        this.accion = accion;
        this.plantaId = plantaId;
    }

    public Long getId() {
        return id;
    }

    public Long getBloqueoId() {
        return bloqueoId;
    }

    public Long getTecnicoId() {
        return tecnicoId;
    }

    public String getAccion() {
        return accion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
