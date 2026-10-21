-- Vinculo propuesta -> proyecto de CIMAxios (flujo comercial).
-- Base: crm-marketing, esquema schema_marketing. Se puede ejecutar varias veces.
--
-- Hibernate (ddl-auto=update) crea la columna proposals.project_id al desplegar,
-- asi que este script es opcional: sirve para crearla antes del despliegue o en
-- un entorno donde ddl-auto este desactivado. No hay tablas nuevas: el embudo,
-- el puntaje de interes, la ficha 360 y el panel Hoy se calculan al vuelo.

SET search_path TO schema_marketing;

ALTER TABLE schema_marketing.proposals
    ADD COLUMN IF NOT EXISTS project_id VARCHAR(36);

COMMENT ON COLUMN schema_marketing.proposals.project_id IS
    'Proyecto de CIMAxios creado a partir de la propuesta aprobada (opcional).';

-- Verificacion: la columna existe y es nullable.
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = 'schema_marketing' AND table_name = 'proposals' AND column_name = 'project_id';
