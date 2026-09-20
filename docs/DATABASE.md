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

---

## 3. Principio de No-Invasión de Esquemas (*Zero Cross-Schema JOINs*)

Para preservar la independencia de microservicios y evitar acoplamiento relacional:

1. **Prohibición de Consultas Cruzadas**: `crm-marketing` **jamás ejecuta `JOIN` ni consultas directas** hacia `schema_collab` o `schema_auth`.
2. **Proyecciones Locales Sincronizadas**:
   - Las tablas `CLIENTS` y `PROJECTS` en `schema_marketing` son modelos de lectura desnormalizados.
   - El servicio `CrmSyncService` actualiza estas tablas periódicamente consumiendo los endpoints del CRM a través de KrakenD.
   - Esto permite que los cálculos pesados de analítica se ejecuten a máxima velocidad local sin saturar las bases de datos transaccionales de proyectos o identidades.
