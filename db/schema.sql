-- SILBOM: esquema reproducible sin DROP TABLE ni borrado de datos.
-- Ejecutar como migrador/propietario: psql -v ON_ERROR_STOP=1 -d silbom_db -f db/schema.sql
-- app_role permite usar un rol de aplicación distinto de silbom_app.
\if :{?app_role}
\else
\set app_role silbom_app
\endif
BEGIN;
CREATE TABLE IF NOT EXISTS maquinas (
    id VARCHAR(20) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,
    icono VARCHAR(10) NOT NULL,
    horas_operativas NUMERIC(10, 1) NOT NULL DEFAULT 0.0 CHECK (horas_operativas >= 0.0),
    fallas_correctivas INT NOT NULL DEFAULT 0 CHECK (fallas_correctivas >= 0),
    mtbf NUMERIC(10, 1) NOT NULL DEFAULT 0.0 CHECK (mtbf >= 0.0),
    bloqueada BOOLEAN NOT NULL DEFAULT FALSE,
    bloqueos_activos INT NOT NULL DEFAULT 0 CHECK (bloqueos_activos >= 0),
    planta_id VARCHAR(50) NOT NULL DEFAULT 'PLANTA-SAN-JOAQUIN',
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_maquina_id_formato CHECK (id ~ '^EQ-[A-Z]{2,4}-[0-9]{2}$')
);

COMMENT ON TABLE maquinas IS 'Inventario de maquinas de planta protegidas bajo protocolo LOTO';
COMMENT ON COLUMN maquinas.id IS 'Identificador estandar industrial (ej: EQ-ENV-01)';
COMMENT ON COLUMN maquinas.mtbf IS 'Mean Time Between Failures: horas_operativas / fallas_correctivas (1 decimal)';
COMMENT ON COLUMN maquinas.bloqueos_activos IS 'Contador concurrente Multi-LOTO de tecnicos interviniendo';

-- --------------------------------------------------------------------
-- TABLA 2: BLOQUEOS_LOTO (Auditoría de tickets de intervención)
-- --------------------------------------------------------------------
-- QUÉ HACE: Registra cada candado digital colocado sobre una máquina.
-- POR QUÉ: Exige el cumplimiento legal del candado físico (DS 594),
-- audita fecha/hora de inicio/fin, técnico responsable y motivo sanitizado.
CREATE TABLE IF NOT EXISTS bloqueos_loto (
    id BIGSERIAL PRIMARY KEY,
    maquina_id VARCHAR(20) NOT NULL,
    tipo_intervencion VARCHAR(20) NOT NULL CHECK (tipo_intervencion IN ('CORRECTIVA', 'PREVENTIVA')),
    motivo VARCHAR(250) NOT NULL,
    nombre_tecnico VARCHAR(80) NOT NULL,
    confirmacion_candado_ds594 BOOLEAN NOT NULL CHECK (confirmacion_candado_ds594 = TRUE),
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'CERRADO')),
    hora_inicio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hora_fin TIMESTAMP,
    duracion_minutos BIGINT,
    notas_auditoria_cifradas TEXT,
    planta_id VARCHAR(50) NOT NULL DEFAULT 'PLANTA-SAN-JOAQUIN',
    CONSTRAINT fk_bloqueo_maquina FOREIGN KEY (maquina_id) REFERENCES maquinas(id) ON DELETE RESTRICT
);

COMMENT ON TABLE bloqueos_loto IS 'Historial de auditoria de bloqueos de seguridad LOTO (Energia Cero)';
COMMENT ON COLUMN bloqueos_loto.confirmacion_candado_ds594 IS 'Declaracion jurada legal obligatoria: candado fisico instalado (DS 594)';
COMMENT ON COLUMN bloqueos_loto.notas_auditoria_cifradas IS 'Notas confidenciales encriptadas con AES-256-GCM en reposo';


\ir migrations/V2__integridad_loto.sql
GRANT USAGE ON SCHEMA public TO :"app_role";
GRANT SELECT, INSERT, UPDATE ON maquinas,tecnicos,bloqueos_loto TO :"app_role";
GRANT SELECT, INSERT ON fallas,eventos_loto TO :"app_role";
REVOKE DELETE ON maquinas,tecnicos,bloqueos_loto,fallas,eventos_loto FROM :"app_role";
REVOKE UPDATE ON eventos_loto,fallas FROM :"app_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"app_role";
COMMIT;
