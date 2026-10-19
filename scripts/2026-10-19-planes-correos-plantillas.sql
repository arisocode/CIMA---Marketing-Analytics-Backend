-- Planes configurables, estado de entrega de correos y plantillas de mensajes.
-- Base: crm-marketing, esquema schema_marketing. Se puede ejecutar varias veces.
--
-- Lo que crea Hibernate solo (ddl-auto=update) al desplegar el backend:
--   * tablas marketing_plans, marketing_message_templates y marketing_email_deliveries;
--   * columnas marketing_interactions.delivery_status y .delivery_error;
--   * columna workflows.template_id.
-- Al arrancar, el backend siembra Platinum, Oro y Diamante si marketing_plans esta vacia.
--
-- Lo que ddl-auto=update NO resuelve, y hace este script:
--   1) Quitar el CHECK de clients.plan (lista fija de planes). Sin esto, asignar
--      un plan nuevo creado por CIMA falla con un error de restriccion.
--   2) Registrar como planes INACTIVOS los valores de clients.plan que no esten en
--      marketing_plans (solo si la tabla ya existe), para que se vean en Analitica
--      y se puedan reactivar o renombrar desde la aplicacion.
--
-- Orden recomendado: desplegar el backend y luego ejecutar el script. Si se
-- ejecuta antes del despliegue, el paso 2 se omite solo; vuelva a ejecutarlo despues.

SET search_path TO schema_marketing;

-- Diagnostico previo: planes en uso.
SELECT plan, COUNT(*) AS clientes FROM clients GROUP BY plan ORDER BY plan;

BEGIN;

-- 1) Quitar cualquier CHECK sobre clients.plan (el de Hibernate y clients_plan_check
--    del script del 2026-09-28).
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT con.conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE nsp.nspname = 'schema_marketing'
      AND rel.relname = 'clients'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) ILIKE '%plan%'
  LOOP
    EXECUTE format('ALTER TABLE schema_marketing.clients DROP CONSTRAINT %I', r.conname);
    RAISE NOTICE 'Restricción eliminada: %', r.conname;
  END LOOP;
END $$;

-- 2) Planes en uso que no estan en el catalogo -> se registran inactivos.
DO $$
BEGIN
  IF to_regclass('schema_marketing.marketing_plans') IS NULL THEN
    RAISE NOTICE 'marketing_plans aún no existe: despliegue el backend y vuelva a ejecutar el script.';
  ELSE
    INSERT INTO schema_marketing.marketing_plans
        (plan_id, name, color, description, sort_order, active, created_at)
    SELECT gen_random_uuid(),
           enuso.plan,
           '#6B7280',
           'Plan heredado registrado por la migración del 2026-10-19.',
           100 + ROW_NUMBER() OVER (ORDER BY enuso.plan),
           false,
           now()
    FROM (SELECT DISTINCT trim(plan) AS plan
          FROM schema_marketing.clients
          WHERE plan IS NOT NULL AND trim(plan) <> '') enuso
    WHERE NOT EXISTS (
        SELECT 1 FROM schema_marketing.marketing_plans p
        WHERE lower(p.name) = lower(enuso.plan));
  END IF;
END $$;

COMMIT;

-- Verificacion 1: no debe quedar ningun CHECK sobre clients.plan (resultado vacio).
SELECT con.conname, pg_get_constraintdef(con.oid)
FROM pg_constraint con
JOIN pg_class rel ON rel.oid = con.conrelid
JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
WHERE nsp.nspname = 'schema_marketing' AND rel.relname = 'clients'
  AND con.contype = 'c' AND pg_get_constraintdef(con.oid) ILIKE '%plan%';

-- Verificacion 2 (despues del despliegue): catalogo de planes y clientes por plan.
-- SELECT p.name, p.active, p.sort_order, COUNT(c.client_id) AS clientes
-- FROM schema_marketing.marketing_plans p
-- LEFT JOIN schema_marketing.clients c ON lower(c.plan) = lower(p.name)
-- GROUP BY p.name, p.active, p.sort_order
-- ORDER BY p.sort_order;
