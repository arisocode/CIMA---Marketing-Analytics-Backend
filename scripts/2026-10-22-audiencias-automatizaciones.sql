-- Audiencias de campaña, segmentos guardados y automatizaciones.
-- Base: crm-marketing, esquema schema_marketing. Idempotente: se puede ejecutar varias veces.
--
-- Ejecutar ANTES de desplegar el backend (o justo despues, antes de usar las funciones nuevas).
-- ddl-auto=update crea las columnas y la tabla nuevas, pero NO:
--   * quita el NOT NULL de campaigns.client_id (las campañas SEGMENT y ALL no tienen cliente);
--   * actualiza el CHECK de workflows.action_type (sin esto, guardar send_portal_message falla);
--   * marca las campañas existentes como CLIENT ni da fecha a los workflows scheduled_date existentes.

SET search_path TO schema_marketing;

BEGIN;

-- 1) Audiencia de campaña
ALTER TABLE schema_marketing.campaigns ADD COLUMN IF NOT EXISTS audience_type VARCHAR(10);
ALTER TABLE schema_marketing.campaigns ADD COLUMN IF NOT EXISTS segment_id UUID;
UPDATE schema_marketing.campaigns SET audience_type = 'CLIENT' WHERE audience_type IS NULL;
ALTER TABLE schema_marketing.campaigns ALTER COLUMN client_id DROP NOT NULL;

-- 2) Segmentos guardados
CREATE TABLE IF NOT EXISTS schema_marketing.marketing_segments (
    segment_id  UUID         PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    criteria    TEXT         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_campaigns_segment ON schema_marketing.campaigns (segment_id);

-- 3) Workflows: fecha programada y nueva accion send_portal_message
ALTER TABLE schema_marketing.workflows ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP;

-- Los scheduled_date existentes corrian apenas la campaña estaba vigente: se conserva
-- ese comportamiento dandoles como fecha programada su fecha de creacion.
UPDATE schema_marketing.workflows
   SET scheduled_at = COALESCE(created_at, now())
 WHERE trigger_type = 'scheduled_date' AND scheduled_at IS NULL;

DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT con.conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE nsp.nspname = 'schema_marketing'
      AND rel.relname = 'workflows'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) ILIKE '%action_type%'
  LOOP
    EXECUTE format('ALTER TABLE schema_marketing.workflows DROP CONSTRAINT %I', r.conname);
    RAISE NOTICE 'Restricción eliminada: %', r.conname;
  END LOOP;
END $$;

ALTER TABLE schema_marketing.workflows
    ADD CONSTRAINT workflows_action_type_check
    CHECK (action_type IN ('send_email', 'send_whatsapp', 'log_followup', 'notify_admin', 'send_portal_message'));

-- 4) Bandeja de WhatsApp pendientes (channel whatsapp + SIMULATED)
CREATE INDEX IF NOT EXISTS idx_interactions_channel_status
    ON schema_marketing.marketing_interactions (channel, delivery_status);

COMMIT;

-- Verificacion 1: campañas por audiencia (todas deben tener audience_type).
SELECT audience_type, COUNT(*) AS campanas FROM schema_marketing.campaigns GROUP BY audience_type;

-- Verificacion 2: client_id nullable y CHECK de action_type con send_portal_message.
SELECT column_name, is_nullable FROM information_schema.columns
WHERE table_schema = 'schema_marketing' AND table_name = 'campaigns' AND column_name = 'client_id';
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid = 'schema_marketing.workflows'::regclass AND contype = 'c';

-- Verificacion 3: workflows scheduled_date sin fecha (resultado vacio).
SELECT workflow_id, workflow_name FROM schema_marketing.workflows
WHERE trigger_type = 'scheduled_date' AND scheduled_at IS NULL;
