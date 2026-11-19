package cl.silbom.backend.config;

import cl.silbom.backend.model.BloqueoLoto;
import cl.silbom.backend.model.EstadoBloqueo;
import cl.silbom.backend.model.EventoLoto;
import cl.silbom.backend.model.Maquina;
import cl.silbom.backend.model.Rol;
import cl.silbom.backend.model.Tecnico;
import cl.silbom.backend.model.TipoIntervencion;
import cl.silbom.backend.repository.BloqueoLotoRepository;
import cl.silbom.backend.repository.EventoLotoRepository;
import cl.silbom.backend.repository.MaquinaRepository;
import cl.silbom.backend.repository.TecnicoRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * QUÉ HACE: Crea la primera cuenta supervisora si se configuró bootstrap y carga datos de
 * demostración cuando no existen máquinas de la planta.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: No hay contraseña inicial por defecto ni se reemplazan registros
 * existentes. Los tres tickets históricos se marcan LEGADO_IMPORTADO; no se inventa una identidad
 * autenticada para datos del prototipo.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final MaquinaRepository maquinaRepository;

    private final BloqueoLotoRepository bloqueoLotoRepository;

    private final String plantaId;

    private final TecnicoRepository tecnicos;

    private final EventoLotoRepository eventos;

    private final PasswordEncoder encoder;

    private final String bootstrapUsername;

    private final String bootstrapPassword;

    public DataInitializer(
            MaquinaRepository maquinaRepository,
            BloqueoLotoRepository bloqueoLotoRepository,
            @Value("${silbom.security.planta-id:PLANTA-SAN-JOAQUIN}") String plantaId,
            TecnicoRepository tecnicos,
            EventoLotoRepository eventos,
            PasswordEncoder encoder,
            @Value("${silbom.bootstrap.username:}") String bootstrapUsername,
            @Value("${silbom.bootstrap.password:}") String bootstrapPassword) {
        this.maquinaRepository = maquinaRepository;
        this.bloqueoLotoRepository = bloqueoLotoRepository;
        this.plantaId = plantaId;
        this.tecnicos = tecnicos;
        this.eventos = eventos;
        this.encoder = encoder;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    /**
     * QUÉ HACE: Valida las credenciales iniciales, aplica BCrypt y registra los equipos, tickets y
     * eventos de demostración en una transacción.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Exige al menos 12 caracteres y como máximo 72 bytes UTF-8 por el
     * límite de BCrypt; rechaza CAMBIAR_ para no aceptar el marcador de ejemplo. El evento
     * LEGADO_IMPORTADO identifica datos anteriores, sin afirmar que sus acciones fueron observadas
     * por el sistema.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (tecnicos.findByPlantaIdAndActivoTrueOrderById(plantaId).isEmpty()
                && !bootstrapUsername.isBlank()) {
            if (!bootstrapUsername.matches("[A-Za-z0-9._-]{3,50}")
                    || bootstrapPassword.startsWith("CAMBIAR_")
                    || bootstrapPassword.length() < 12
                    || bootstrapPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            > 72) {
                throw new IllegalArgumentException(
                        "Bootstrap requiere usuario válido y contraseña de 12 caracteres / máximo"
                            + " 72 bytes");
            }
            tecnicos.save(
                    new Tecnico(
                            bootstrapUsername,
                            "Supervisor inicial",
                            encoder.encode(bootstrapPassword),
                            Rol.SUPERVISOR,
                            plantaId));
            log.info(
                    "Cuenta supervisora inicial creada; retire las variables de bootstrap del"
                        + " entorno");
        }
        if (maquinaRepository.count() == 0) {
            log.info("Inicializando datos maestros de Planta San Joaquín...");
            List<Maquina> maquinasIniciales =
                    List.of(
                            new Maquina(
                                    "EQ-ENV-01",
                                    "Envasadora Rotativa Automática",
                                    "Línea de Envasado 1",
                                    "📦",
                                    701.5,
                                    3,
                                    this.plantaId),
                            new Maquina(
                                    "EQ-PR-04",
                                    "Prensa Hidráulica 200T",
                                    "Área de Estampado",
                                    "⚙️",
                                    712.0,
                                    2,
                                    this.plantaId),
                            new Maquina(
                                    "EQ-EXT-02",
                                    "Extrusora de Polímeros",
                                    "Área de Moldeo",
                                    "🔄",
                                    685.2,
                                    4,
                                    this.plantaId),
                            new Maquina(
                                    "EQ-MEZ-01",
                                    "Mezclador Industrial de Cinta",
                                    "Área de Preparación",
                                    "🥣",
                                    715.0,
                                    1,
                                    this.plantaId),
                            new Maquina(
                                    "EQ-COM-03",
                                    "Compresor de Tornillo",
                                    "Sala de Máquinas",
                                    "💨",
                                    718.5,
                                    1,
                                    this.plantaId),
                            new Maquina(
                                    "EQ-TR-01",
                                    "Cinta Transportadora Principal",
                                    "Logística y Despacho",
                                    "🏗️",
                                    708.0,
                                    2,
                                    this.plantaId));
            maquinaRepository.saveAll(maquinasIniciales);
            log.info("Se cargaron exitosamente las 6 máquinas industriales.");
            // Cargar los 3 bloqueos cerrados iniciales
            LocalDateTime ahora = LocalDateTime.now();
            List<BloqueoLoto> historialInicial =
                    List.of(
                            new BloqueoLoto(
                                    "EQ-PR-04",
                                    TipoIntervencion.PREVENTIVA,
                                    "Inspección y lubricación semanal programada",
                                    "Carlos R.",
                                    true,
                                    EstadoBloqueo.CERRADO,
                                    ahora.minusDays(3),
                                    ahora.minusDays(3).plusMinutes(45),
                                    45L,
                                    this.plantaId),
                            new BloqueoLoto(
                                    "EQ-EXT-02",
                                    TipoIntervencion.CORRECTIVA,
                                    "Sobrecalentamiento en motor principal (Eléctrico)",
                                    "Vicente M.",
                                    true,
                                    EstadoBloqueo.CERRADO,
                                    ahora.minusDays(2),
                                    ahora.minusDays(2).plusMinutes(72),
                                    72L,
                                    this.plantaId),
                            new BloqueoLoto(
                                    "EQ-ENV-01",
                                    TipoIntervencion.CORRECTIVA,
                                    "Atascamiento de cabezal móvil (Mecánico)",
                                    "Benjamín V.",
                                    true,
                                    EstadoBloqueo.CERRADO,
                                    ahora.minusDays(1),
                                    ahora.minusDays(1).plusMinutes(35),
                                    35L,
                                    this.plantaId));
            bloqueoLotoRepository
                    .saveAll(historialInicial)
                    .forEach(
                            b ->
                                    eventos.save(
                                            new EventoLoto(
                                                    b.getId(),
                                                    null,
                                                    "LEGADO_IMPORTADO",
                                                    plantaId)));
            log.info("Se cargaron exitosamente los 3 tickets históricos cerrados.");
        } else {
            log.info(
                    "La base de datos ya contiene registros maestros (total: {} máquinas)."
                        + " Omitiendo seed inicial.",
                    maquinaRepository.count());
        }
    }
}
