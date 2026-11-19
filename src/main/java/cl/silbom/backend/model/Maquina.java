package cl.silbom.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * QUÉ HACE: Representa el equipo, sus métricas declaradas, el número de tickets activos y su estado
 * digital de bloqueo.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Los métodos de dominio restringen cambios de contadores, MTBF y baja
 * lógica. El estado digital no prueba energía cero ni sustituye el aislamiento físico.
 */
@Entity
@Table(name = "maquinas")
public class Maquina {

    @Id
    @Column(name = "id", length = 20, nullable = false, updatable = false)
    private String id;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "area", length = 100, nullable = false)
    private String area;

    @Column(name = "icono", length = 10, nullable = false)
    private String icono;

    @Column(name = "horas_operativas", nullable = false)
    private Double horasOperativas;

    @Column(name = "fallas_correctivas", nullable = false)
    private Integer fallasCorrectivas;

    @Column(name = "mtbf")
    private Double mtbf;

    @Column(name = "bloqueada", nullable = false)
    private Boolean bloqueada;

    @Column(name = "bloqueos_activos", nullable = false)
    private Integer bloqueosActivos;

    @Column(name = "planta_id", length = 50, nullable = false)
    private String plantaId;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", nullable = false)
    private LocalDateTime fechaModificacion;

    protected Maquina() {}

    public Maquina(
            String id,
            String nombre,
            String area,
            String icono,
            Double horasOperativas,
            Integer fallasCorrectivas,
            String plantaId) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El ID de la máquina no puede ser nulo ni vacío");
        }
        this.id = id;
        this.nombre = nombre;
        this.area = area;
        this.icono = (icono != null && !icono.isBlank()) ? icono : "⚙️";
        validarMedicion(horasOperativas, fallasCorrectivas);
        this.horasOperativas = horasOperativas;
        this.fallasCorrectivas = fallasCorrectivas;
        this.bloqueada = false;
        this.bloqueosActivos = 0;
        this.plantaId = (plantaId != null && !plantaId.isBlank()) ? plantaId : "PLANTA-SAN-JOAQUIN";
        this.fechaCreacion = LocalDateTime.now();
        this.fechaModificacion = LocalDateTime.now();
        this.recalcularMtbf();
    }

    /**
     * QUÉ HACE: Calcula horas operativas / fallas observadas y redondea a un decimal; sin fallas
     * devuelve null.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Cero fallas no permite calcular un promedio entre fallas y no
     * debe sustituirse por las horas operativas. Las horas corresponden a datos declarados, no al
     * tiempo de reloj ni a una predicción exacta.
     */
    public void recalcularMtbf() {
        if (this.fallasCorrectivas == null || this.fallasCorrectivas <= 0) {
            this.mtbf = null;
        } else {
            double valor = this.horasOperativas / this.fallasCorrectivas;
            this.mtbf = Math.round(valor * 10.0) / 10.0;
        }
    }

    /**
     * QUÉ HACE: Incrementa los bloqueos activos y marca el equipo digitalmente bloqueado; cuenta
     * una falla solo si nuevaFallaCorrectiva es true.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El servicio decide si la referencia de avería es nueva bajo lock
     * de máquina. Otros técnicos de la misma falla y las preventivas no deben volver a penalizar el
     * MTBF.
     */
    public void registrarEntradaBloqueo(boolean nuevaFallaCorrectiva) {
        if (bloqueosActivos != null && bloqueosActivos == Integer.MAX_VALUE
                || nuevaFallaCorrectiva
                        && fallasCorrectivas != null
                        && fallasCorrectivas == Integer.MAX_VALUE) {
            throw new IllegalStateException("Contador fuera de rango");
        }
        if (this.bloqueosActivos == null) {
            this.bloqueosActivos = 0;
        }
        this.bloqueosActivos++;
        this.bloqueada = true;
        if (nuevaFallaCorrectiva) {
            if (this.fallasCorrectivas == null) {
                this.fallasCorrectivas = 0;
            }
            this.fallasCorrectivas++;
            this.recalcularMtbf();
        }
        this.fechaModificacion = LocalDateTime.now();
    }

    /**
     * QUÉ HACE: Descuenta un ticket y mantiene el bloqueo digital hasta que el contador llegue a
     * cero.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Rechaza contadores inconsistentes; el servicio protege la
     * operación con locks y comprueba que el ticket no haya sido liberado antes.
     */
    public void registrarSalidaBloqueo() {
        if (this.bloqueosActivos == null || this.bloqueosActivos <= 0) {
            throw new IllegalStateException(
                    "Contador de bloqueos inconsistente; requiere revisión");
        }
        this.bloqueosActivos--;
        if (this.bloqueosActivos == null || this.bloqueosActivos == 0) {
            this.bloqueosActivos = 0;
            this.bloqueada = false;
        }
        this.fechaModificacion = LocalDateTime.now();
    }

    /**
     * QUÉ HACE: Actualiza los atributos permitidos y recalcula el MTBF con las mediciones
     * recibidas.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Los contadores LOTO, el ID y la planta no se reciben como campos
     * modificables del DTO administrativo.
     */
    public void actualizarDatos(
            String nombre,
            String area,
            String icono,
            Double horasOperativas,
            Integer fallasCorrectivas) {
        validarMedicion(horasOperativas, fallasCorrectivas);
        this.nombre = nombre;
        this.area = area;
        if (icono != null && !icono.isBlank()) {
            this.icono = icono;
        }
        if (horasOperativas != null && horasOperativas >= 0) {
            this.horasOperativas = horasOperativas;
        }
        if (fallasCorrectivas != null && fallasCorrectivas >= 0) {
            this.fallasCorrectivas = fallasCorrectivas;
        }
        this.recalcularMtbf();
        this.fechaModificacion = LocalDateTime.now();
    }

    /**
     * QUÉ HACE: Rechaza horas no finitas, negativas o fuera de rango y conteos de fallas negativos.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Evita métricas inválidas y valores incompatibles con la
     * persistencia, incluso al invocar el dominio fuera de HTTP.
     */
    private static void validarMedicion(Double horas, Integer fallas) {
        if (horas == null || !Double.isFinite(horas) || horas < 0 || horas > 999999999.9) {
            throw new IllegalArgumentException("Horas operativas fuera de rango");
        }
        if (fallas == null || fallas < 0) {
            throw new IllegalArgumentException("El número de fallas debe ser no negativo");
        }
    }

    /**
     * QUÉ HACE: Da de baja lógica a la máquina cuando no registra bloqueos activos.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Preserva referencias históricas y evita retirar del catálogo un
     * equipo con tickets activos.
     */
    public void desactivar() {
        if (bloqueosActivos > 0 || bloqueada) {
            throw new IllegalStateException(
                    "No puede darse de baja una máquina con bloqueos activos");
        }
        activa = false;
        fechaModificacion = LocalDateTime.now();
    }

    // ====================================================================
    // GETTERS LIMPIOS (Solo lectura, sin setters públicos de mutación libre)
    // ====================================================================
    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getArea() {
        return area;
    }

    public String getIcono() {
        return icono;
    }

    public Double getHorasOperativas() {
        return horasOperativas;
    }

    public Integer getFallasCorrectivas() {
        return fallasCorrectivas;
    }

    public Double getMtbf() {
        return mtbf;
    }

    public Boolean getBloqueada() {
        return bloqueada;
    }

    public Integer getBloqueosActivos() {
        return bloqueosActivos;
    }

    public String getPlantaId() {
        return plantaId;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }
}
