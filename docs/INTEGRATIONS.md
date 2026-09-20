# Integraciones y Plataforma: `crm-marketing`

Este documento describe la integración de `crm-marketing` con el API Gateway KrakenD, la sincronización de datos con el CRM y la interacción con el motor de correos de `crm-media`.

---

## 1. Topología de Integración

```text
               ┌───────────────────────┐
               │    KrakenD Gateway    │
               └───────────┬───────────┘
                           │ (Peticiones Entrantes)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                        crm-marketing                        │
│               (Módulos Marketing y Analytics)               │
└──────────────┬───────────────────────────────┬──────────────┘
               │                               │
               │ (Consulta Proyectos/Clientes) │ (Despacho de Correos)
               ▼                               ▼
      ┌──────────────────┐            ┌──────────────────┐
      │    crm-collab    │            │    crm-media     │
      │  (Vía Gateway)   │            │   (Vía HTTP M2M) │
      └──────────────────┘            └──────────────────┘
```

---

## 2. Integración con KrakenD API Gateway

- **Manifiesto del Servicio**: [`gateway/gateway.manifest.json`](file:///d:/BACKUP%20CELULAR%20OLIMPO/crm-marketing/gateway/gateway.manifest.json).
- **Consolidación en Infra**: Durante el build de infraestructura, `crm-infra` lee el manifiesto de marketing y genera las 30+ rutas correspondientes en `krakend.json`.
- **Rutas de Salud**:
  - `GET /api/v1/health` responde el estado del datasource y los hilos de ejecución de Spring.
  - KrakenD monitorea este endpoint aplicando disyuntores de circuito (*circuit breakers*) automáticos si se detectan 3 fallos consecutivos.

---

## 3. Sincronización de Datos con el CRM (`CrmSyncService`)

Para mantener las proyecciones de analítica al día sin consultar directamente las bases de datos de otros microservicios:

1. **Configuración de Sincronización**:
   - `cimaxis.crm.sync.enabled=true`: Habilita el sincronizador.
   - `cimaxis.crm.sync.on-startup=true`: Ejecuta una sincronización no bloqueante al iniciar. Si el CRM no responde, el servicio arranca con una advertencia en el log (*fail-safe*).
   - `cimaxis.crm.sync.cron=0 0 */6 * * *`: Tarea programada que refresca clientes y proyectos cada 6 horas.
2. **Canal de Consulta**: Invoca los endpoints públicos del CRM a través de `CRM_BASE_URL`, poblando las tablas locales `CLIENTS` y `PROJECTS` en `schema_marketing`.

---

## 4. Tareas Programadas y Automatización (*Schedulers*)

El microservicio utiliza el planificador nativo de Spring (`@Scheduled`):

- **Consolidación de KPIs (`cimaxis.kpi.cron=0 30 1 * * *`)**:
  - Se ejecuta todas las noches a la 1:30 AM.
  - Calcula agregaciones de conversión, volumen de facturación y efectividad de campañas, guardando el registro histórico en `KPI_SNAPSHOTS`.
- **Ejecución de Workflows (`cimaxis.scheduler.cron=0 0 * * * *`)**:
  - Sondea cada hora reglas de inactividad comercial (ej. clientes sin contacto en 15 días) y dispara alertas operativas.
  - Gestiona reintentos con intervalo de 60 minutos y tope de 3 intentos fallidos.
