-- Migración aditiva y repetible. Ejecutar como propietario/migrador, nunca desde la API.
ALTER TABLE maquinas ADD COLUMN IF NOT EXISTS activa BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE maquinas ALTER COLUMN mtbf DROP NOT NULL;
ALTER TABLE maquinas ALTER COLUMN mtbf DROP DEFAULT;
UPDATE maquinas SET mtbf = NULL WHERE fallas_correctivas = 0 AND mtbf IS NOT NULL;

CREATE TABLE IF NOT EXISTS tecnicos (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('TECNICO','SUPERVISOR')),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    planta_id VARCHAR(50) NOT NULL,
    UNIQUE (planta_id, username), UNIQUE(id,planta_id)
);
CREATE TABLE IF NOT EXISTS fallas (
    id BIGSERIAL PRIMARY KEY,
    maquina_id VARCHAR(20) NOT NULL REFERENCES maquinas(id),
    planta_id VARCHAR(50) NOT NULL,
    referencia VARCHAR(80) NOT NULL,
    UNIQUE(planta_id,maquina_id,referencia), UNIQUE(id,maquina_id,planta_id)
);
ALTER TABLE bloqueos_loto ADD COLUMN IF NOT EXISTS tecnico_id BIGINT;
ALTER TABLE bloqueos_loto ADD COLUMN IF NOT EXISTS falla_id BIGINT;
CREATE TABLE IF NOT EXISTS eventos_loto (
    id BIGSERIAL PRIMARY KEY,
    bloqueo_id BIGINT NOT NULL REFERENCES bloqueos_loto(id),
    tecnico_id BIGINT,
    accion VARCHAR(30) NOT NULL CHECK (accion IN ('CREADO','LIBERADO','LEGADO_IMPORTADO')),
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    planta_id VARCHAR(50) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_maquina_planta ON maquinas(id,planta_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_bloqueo_planta ON bloqueos_loto(id,planta_id);
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='maquinas'::regclass AND conname='chk_estado_digital') THEN
        ALTER TABLE maquinas ADD CONSTRAINT chk_estado_digital CHECK (bloqueada = (bloqueos_activos > 0));
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='bloqueos_loto'::regclass AND conname='fk_bloqueo_tecnico_planta') THEN
        ALTER TABLE bloqueos_loto ADD CONSTRAINT fk_bloqueo_tecnico_planta FOREIGN KEY(tecnico_id,planta_id) REFERENCES tecnicos(id,planta_id);
        ALTER TABLE bloqueos_loto ADD CONSTRAINT fk_bloqueo_falla_planta FOREIGN KEY(falla_id,maquina_id,planta_id) REFERENCES fallas(id,maquina_id,planta_id);
        ALTER TABLE bloqueos_loto ADD CONSTRAINT fk_bloqueo_maquina_planta FOREIGN KEY(maquina_id,planta_id) REFERENCES maquinas(id,planta_id);
        ALTER TABLE fallas ADD CONSTRAINT fk_falla_maquina_planta FOREIGN KEY(maquina_id,planta_id) REFERENCES maquinas(id,planta_id);
        ALTER TABLE eventos_loto ADD CONSTRAINT fk_evento_bloqueo_planta FOREIGN KEY(bloqueo_id,planta_id) REFERENCES bloqueos_loto(id,planta_id);
        ALTER TABLE eventos_loto ADD CONSTRAINT fk_evento_tecnico_planta FOREIGN KEY(tecnico_id,planta_id) REFERENCES tecnicos(id,planta_id);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_maquina_catalogo ON maquinas(planta_id,activa,id);
CREATE INDEX IF NOT EXISTS ix_bloqueo_filtros ON bloqueos_loto(planta_id,maquina_id,estado,hora_inicio DESC,id DESC);
CREATE INDEX IF NOT EXISTS ix_bloqueo_tecnico ON bloqueos_loto(tecnico_id,estado);
CREATE INDEX IF NOT EXISTS ix_evento_ticket ON eventos_loto(planta_id,bloqueo_id,id);

-- No se inventan identidades de tickets históricos; se registra su incorporación.
INSERT INTO eventos_loto(bloqueo_id,tecnico_id,accion,fecha,planta_id)
SELECT b.id,NULL,'LEGADO_IMPORTADO',b.hora_inicio,b.planta_id FROM bloqueos_loto b
WHERE NOT EXISTS(SELECT 1 FROM eventos_loto e WHERE e.bloqueo_id=b.id);

CREATE OR REPLACE FUNCTION silbom_historial_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El historial de seguridad es inmutable' USING ERRCODE='23514';
END $$;
CREATE OR REPLACE FUNCTION silbom_ticket_transicion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'No se eliminan tickets LOTO' USING ERRCODE='23514';
    END IF;
    IF OLD.estado <> 'ACTIVO' OR NEW.estado <> 'CERRADO'
       OR NEW.hora_fin IS NULL OR NEW.duracion_minutos IS NULL OR NEW.duracion_minutos < 1
       OR (to_jsonb(NEW) - ARRAY['estado','hora_fin','duracion_minutos']) IS DISTINCT FROM
          (to_jsonb(OLD) - ARRAY['estado','hora_fin','duracion_minutos']) THEN
        RAISE EXCEPTION 'Transición LOTO inválida o modificación de historial' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS ticket_transicion ON bloqueos_loto;
CREATE TRIGGER ticket_transicion BEFORE UPDATE OR DELETE ON bloqueos_loto FOR EACH ROW EXECUTE FUNCTION silbom_ticket_transicion();
DROP TRIGGER IF EXISTS evento_inmutable ON eventos_loto;
CREATE TRIGGER evento_inmutable BEFORE UPDATE OR DELETE ON eventos_loto FOR EACH ROW EXECUTE FUNCTION silbom_historial_inmutable();
DROP TRIGGER IF EXISTS falla_inmutable ON fallas;
CREATE TRIGGER falla_inmutable BEFORE UPDATE OR DELETE ON fallas FOR EACH ROW EXECUTE FUNCTION silbom_historial_inmutable();

-- Denegación por defecto sin contexto. La aplicación fija SET LOCAL en cada transacción.
-- El nombre de la política original de tickets no coincidía con el de la tabla.
DROP POLICY IF EXISTS bloqueos_planta_isolation_policy ON bloqueos_loto;
DO $$ DECLARE tabla text; BEGIN
    FOREACH tabla IN ARRAY ARRAY['maquinas','bloqueos_loto','tecnicos','fallas','eventos_loto'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',tabla);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',tabla);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I',tabla||'_planta_isolation_policy',tabla);
        EXECUTE format('CREATE POLICY %I ON %I USING (planta_id = nullif(current_setting(''app.current_planta_id'',true),'''')) WITH CHECK (planta_id = nullif(current_setting(''app.current_planta_id'',true),''''))',tabla||'_planta_isolation_policy',tabla);
    END LOOP;
END $$;
