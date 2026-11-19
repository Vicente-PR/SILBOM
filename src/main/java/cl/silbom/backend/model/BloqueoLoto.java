package cl.silbom.backend.model;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * QUÉ HACE:
 * Entidad JPA que representa un ticket digital de bloqueo LOTO (Lockout/Tagout).
 * Audita quién, cuándo y por qué intervino una máquina específica de la planta.
 *
 * POR QUÉ ESTÁ ESCRITO ASÍ (CONTROL 8 DE CIBERSEGURIDAD: ENCAPSULAMIENTO ESTRICTO):
 * - Atributos 'private' sin setters públicos para 'id', 'horaInicio', 'horaFin', 'estado' ni 'duracionMinutos'.
 * - El cierre del ticket se realiza exclusivamente mediante el método de negocio 'cerrarTicket()',
 *   el cual valida que el ticket no haya sido cerrado previamente y calcula la duración de forma atómica.
 * - Constructor JPA vacío declarado 'protected'.
 */
@Entity
@Table(name = "bloqueos_loto")
public class BloqueoLoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "maquina_id", length = 20, nullable = false)
    private String maquinaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_intervencion", length = 20, nullable = false)
    private TipoIntervencion tipoIntervencion;

    @Column(name = "motivo", length = 250, nullable = false)
    private String motivo;

    @Column(name = "nombre_tecnico", length = 80, nullable = false)
    private String nombreTecnico;

    @Column(name = "confirmacion_candado_ds594", nullable = false)
    private Boolean confirmacionCandadoDs594;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoBloqueo estado;

    @Column(name = "hora_inicio", nullable = false, updatable = false)
    private LocalDateTime horaInicio;

    @Column(name = "hora_fin")
    private LocalDateTime horaFin;

    @Column(name = "duracion_minutos")
    private Long duracionMinutos;

    @Column(name = "notas_auditoria_cifradas", columnDefinition = "TEXT")
    private String notasAuditoriaCifradas;

    @Column(name = "planta_id", length = 50, nullable = false)
    private String plantaId;

    @Column(name = "tecnico_id", updatable = false)
    private Long tecnicoId;
    @Column(name = "falla_id", updatable = false)
    private Long fallaId;

    /**
     * Constructor por defecto requerido obligatoriamente por la especificación de JPA.
     * Se declara 'protected' para impedir instanciaciones vacías e incompletas.
     */
    protected BloqueoLoto() {
    }

    /**
     * Constructor de dominio para dar de alta un nuevo bloqueo en estado ACTIVO.
     */
    public BloqueoLoto(String maquinaId, TipoIntervencion tipoIntervencion, String motivo,
                       String nombreTecnico, Boolean confirmacionCandadoDs594, String plantaId) {
        if (confirmacionCandadoDs594 == null || !confirmacionCandadoDs594) {
            throw new IllegalArgumentException("Debe declarar la instalación del candado físico; esto no verifica energía cero");
        }
        this.maquinaId = maquinaId;
        this.tipoIntervencion = tipoIntervencion;
        this.motivo = motivo;
        this.nombreTecnico = nombreTecnico;
        this.confirmacionCandadoDs594 = true;
        this.estado = EstadoBloqueo.ACTIVO;
        this.horaInicio = LocalDateTime.now();
        this.plantaId = (plantaId != null && !plantaId.isBlank()) ? plantaId : "PLANTA-SAN-JOAQUIN";
    }

    /**
     * Constructor para poblamiento inicial de registros históricos que ya nacen cerrados.
     */
    public BloqueoLoto(String maquinaId, TipoIntervencion tipoIntervencion, String motivo,
                       String nombreTecnico, Boolean confirmacionCandadoDs594, EstadoBloqueo estado,
                       LocalDateTime horaInicio, LocalDateTime horaFin, Long duracionMinutos, String plantaId) {
        this.maquinaId = maquinaId;
        this.tipoIntervencion = tipoIntervencion;
        this.motivo = motivo;
        this.nombreTecnico = nombreTecnico;
        this.confirmacionCandadoDs594 = confirmacionCandadoDs594;
        this.estado = estado;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.duracionMinutos = duracionMinutos;
        this.plantaId = (plantaId != null && !plantaId.isBlank()) ? plantaId : "PLANTA-SAN-JOAQUIN";
    }

    /**
     * QUÉ HACE:
     * Cierra formalmente la intervención LOTO del técnico.
     *
     * POR QUÉ ESTÁ ESCRITO ASÍ:
     * - Valida que no se pueda volver a cerrar un ticket ya cerrado (idempotencia y coherencia).
     * - Asigna la marca temporal 'horaFin' con el momento exacto actual.
     * - Calcula de forma determinista la duración en minutos transcurridos.
     * - Cambia el estado a CERRADO.
     */
    public void cerrarTicket() {
        if (this.estado == EstadoBloqueo.CERRADO) {
            throw new IllegalStateException("El ticket LOTO #" + this.id + " ya se encuentra CERRADO");
        }
        this.horaFin = LocalDateTime.now();
        if (this.horaInicio != null) {
            long minutos = Duration.between(this.horaInicio, this.horaFin).toMinutes();
            this.duracionMinutos = Math.max(minutos, 1L); // Al menos 1 minuto de intervención
        }
        this.estado = EstadoBloqueo.CERRADO;
    }

    /**
     * Asigna notas cifradas de auditoría (Control 5).
     */
    public void setNotasAuditoriaCifradas(String notasCifradas) {
        this.notasAuditoriaCifradas = notasCifradas;
    }

    public void asignarResponsable(Long tecnicoId, Long fallaId) {
        if (this.tecnicoId != null || tecnicoId == null)
            throw new IllegalStateException("El responsable del bloqueo es inmutable");
        this.tecnicoId = tecnicoId;
        this.fallaId = fallaId;
    }
    public Long getTecnicoId() { return tecnicoId; }
    public Long getFallaId() { return fallaId; }

    // ====================================================================
    // GETTERS LIMPIOS (Solo lectura de atributos)
    // ====================================================================

    public Long getId() {
        return id;
    }

    public String getMaquinaId() {
        return maquinaId;
    }

    public TipoIntervencion getTipoIntervencion() {
        return tipoIntervencion;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getNombreTecnico() {
        return nombreTecnico;
    }

    public Boolean getConfirmacionCandadoDs594() {
        return confirmacionCandadoDs594;
    }

    public EstadoBloqueo getEstado() {
        return estado;
    }

    public LocalDateTime getHoraInicio() {
        return horaInicio;
    }

    public LocalDateTime getHoraFin() {
        return horaFin;
    }

    public Long getDuracionMinutos() {
        return duracionMinutos;
    }

    public String getNotasAuditoriaCifradas() {
        return notasAuditoriaCifradas;
    }

    public String getPlantaId() {
        return plantaId;
    }
}
