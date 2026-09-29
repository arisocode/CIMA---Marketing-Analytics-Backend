-- Migración de planes de cliente: Oro/Esmeralda/Premium -> Platinum/Oro/Diamante
-- Ejecutar UNA vez sobre la base de crm-marketing ANTES de desplegar el backend
-- con el nuevo enum Client.Plan { Platinum, Oro, Diamante }.

SET search_path TO schema_marketing;

-- 1) Diagnóstico: cuántos clientes hay con cada plan actual.
SELECT plan, COUNT(*) FROM clients GROUP BY plan ORDER BY plan;

BEGIN;

-- 2) Quitar el CHECK que Hibernate creó para el enum viejo
--    (con ddl-auto=update Hibernate NO lo actualiza solo y rechazaría los valores nuevos).
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
  END LOOP;
END $$;

-- 3) Pasar los datos existentes a los planes nuevos.
--    AJUSTAR según lo que decida el equipo. Por defecto: Esmeralda -> Platinum, Premium -> Diamante.
UPDATE clients SET plan = 'Platinum' WHERE plan = 'Esmeralda';
UPDATE clients SET plan = 'Diamante' WHERE plan = 'Premium';

-- 4) Volver a proteger la columna con los valores válidos.
ALTER TABLE clients
  ADD CONSTRAINT clients_plan_check CHECK (plan IN ('Platinum', 'Oro', 'Diamante'));

COMMIT;

-- 5) Verificación: solo deben aparecer Platinum, Oro, Diamante o NULL.
SELECT plan, COUNT(*) FROM clients GROUP BY plan ORDER BY plan;
