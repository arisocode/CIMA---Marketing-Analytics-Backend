# Base de Datos y Persistencia: `crm-marketing`

Este documento describe el modelo relacional en PostgreSQL, la configuración de Spring Data JPA / Hibernate y las políticas de aislamiento de datos en `schema_marketing`.

---

## 1. Conexión y Esquema Dedicado: `schema_marketing`

`crm-marketing` se conecta a la instancia compartida de PostgreSQL 16 utilizando un esquema lógico aislado:

- **Cadena de Conexión**:
  ```text
  jdbc:postgresql://${DATABASE_HOST}:${DATABASE_PORT}/${DATABASE_NAME}?currentSchema=schema_marketing
  ```
- **Usuario de Base de Datos**: `marketing_user`, cuyos permisos SQL están restringidos exclusivamente al esquema `schema_marketing`.
- **Dialecto Hibernate**: `org.hibernate.dialect.PostgreSQLDialect`.

---

## 2. Mapa de Tablas y Entidades JPA

```text
┌─────────────────────────────────────────────────────────────┐
│                      schema_marketing                       │
├──────────────────────────────┬──────────────────────────────┤
│      Tablas de Marketing     │     Tablas de Analítica      │
├──────────────────────────────┼──────────────────────────────┤
│ • CAMPAIGNS                  │ • CLIENTS (Proyección local) │
│ • PROPOSALS                  │ • PROJECTS (Proyección local)│
│ • INTERACTIONS               │ • KPI_SNAPSHOTS              │
│ • AUDIENCES                  │ • INVENTORY                  │
│ • WORKFLOWS                  │                              │
│ • WORKFLOW_EXECUTIONS        │                              │
└──────────────────────────────┴──────────────────────────────┘
```

### A. Entidades Principales
- **`CAMPAIGNS`**: Custodia campañas publicitarias, presupuestos y rangos de fechas.
- **`PROPOSALS`**: Propuestas comerciales, montos cotizados y estados de aprobación.
- **`INTERACTIONS`**: Historial de comunicaciones comerciales con clientes.
- **`KPI_SNAPSHOTS`**: Registros temporales inmutables con agregaciones numéricas precalculadas.
- **`MARKETING_PLANS`**: Catálogo configurable de planes (nombre único, color, orden, activo). `clients.plan` guarda el nombre del plan como texto, sin CHECK.
- **`MARKETING_MESSAGE_TEMPLATES`**: Plantillas de mensajes por canal (EMAIL, WHATSAPP, IN_APP) y plan opcional, con variables `{nombre}`, `{plan}`, `{empresa}`.
- **`MARKETING_EMAIL_DELIVERIES`**: Registro de cada intento de correo (QUEUED, FAILED, SIMULATED, SKIPPED_LIMIT); con él se calcula el cupo diario (zona America/Bogota).
- Columnas nuevas: `marketing_interactions.delivery_status`, `marketing_interactions.delivery_error` y `workflows.template_id`.
- `PROPOSALS.project_id` (varchar 36, nullable): proyecto de CIMAxios vinculado a una propuesta aprobada. Script opcional: `scripts/2026-10-21-propuesta-proyecto.sql`.
- **`MARKETING_MESSAGES`**: Conversación comercial CIMA ↔ cliente (`direction` OUTBOUND/INBOUND, `body` máx. 4000, `read_at`).
- `marketing_interactions.campaign_id` es nullable (mensajes del portal y decisiones de propuestas); `proposals.client_comment` y `proposals.decided_at`. Script obligatorio: `scripts/2026-10-21-portal-cliente.sql` (ddl-auto no quita el NOT NULL).
- **`MARKETING_SEGMENTS`**: Segmentos guardados (nombre único, `criteria` con el JSON de SegmentCriteria, activo). Los clientes se recalculan en cada consulta.
- `campaigns.audience_type` (CLIENT/SEGMENT/ALL), `campaigns.segment_id`, `campaigns.client_id` nullable; `workflows.scheduled_at`; acción `send_portal_message`; `delivery_status` `SENT_MANUAL`. Script obligatorio: `scripts/2026-10-22-audiencias-automatizaciones.sql`.
- Migración manual: `scripts/2026-10-19-planes-correos-plantillas.sql` (quita el CHECK de `clients.plan` y registra planes heredados como inactivos).

---

## 3. Principio de No-Invasión de Esquemas (*Zero Cross-Schema JOINs*)

Para preservar la independencia de microservicios y evitar acoplamiento relacional:

1. **Prohibición de Consultas Cruzadas**: `crm-marketing` **jamás ejecuta `JOIN` ni consultas directas** hacia `schema_collab` o `schema_auth`.
2. **Proyecciones Locales Sincronizadas**:
   - Las tablas `CLIENTS` y `PROJECTS` en `schema_marketing` son modelos de lectura desnormalizados.
   - El servicio `CrmSyncService` actualiza estas tablas periódicamente consumiendo los endpoints del CRM a través de KrakenD.
   - Esto permite que los cálculos pesados de analítica se ejecuten a máxima velocidad local sin saturar las bases de datos transaccionales de proyectos o identidades.
