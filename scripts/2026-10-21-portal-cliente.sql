-- Portal del cliente: propuestas decididas por el cliente y conversacion comercial.
-- Base: crm-marketing, esquema schema_marketing. Idempotente: se puede ejecutar varias veces.
--
-- IMPORTANTE: ejecutar ANTES de desplegar el backend (o justo despues, antes de usar
-- el portal). ddl-auto=update crea columnas y tablas nuevas, pero NO quita el NOT NULL
-- de marketing_interactions.campaign_id: sin el paso 1, guardar un mensaje del portal
-- o una decision de propuesta falla con "null value in column campaign_id".

SET search_path TO schema_marketing;

BEGIN;

-- 1) Interacciones sin campaña (mensajes del portal y decisiones de propuestas).
ALTER TABLE schema_marketing.marketing_interactions
    ALTER COLUMN campaign_id DROP NOT NULL;

-- 2) Decision del cliente sobre la propuesta.
ALTER TABLE schema_marketing.proposals
    ADD COLUMN IF NOT EXISTS client_comment TEXT;
ALTER TABLE schema_marketing.proposals
    ADD COLUMN IF NOT EXISTS decided_at TIMESTAMP;

-- 3) Conversacion comercial CIMA <-> cliente.
CREATE TABLE IF NOT EXISTS schema_marketing.marketing_messages (
    message_id  UUID         PRIMARY KEY,
    client_id   VARCHAR(36)  NOT NULL,
    direction   VARCHAR(10)  NOT NULL CHECK (direction IN ('OUTBOUND', 'INBOUND')),
    body        TEXT         NOT NULL CHECK (char_length(body) <= 4000),
    author_sub  VARCHAR(36),
    author_name VARCHAR(120),
    created_at  TIMESTAMP    NOT NULL,
    read_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_marketing_messages_client_created
    ON schema_marketing.marketing_messages (client_id, created_at);

COMMIT;

-- Verificacion 1: campaign_id debe ser nullable (is_nullable = YES).
SELECT column_name, is_nullable
FROM information_schema.columns
WHERE table_schema = 'schema_marketing' AND table_name = 'marketing_interactions'
  AND column_name = 'campaign_id';

-- Verificacion 2: columnas nuevas de proposals y tabla de mensajes.
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'schema_marketing'
  AND ((table_name = 'proposals' AND column_name IN ('client_comment', 'decided_at'))
       OR table_name = 'marketing_messages')
ORDER BY table_name, ordinal_position;
