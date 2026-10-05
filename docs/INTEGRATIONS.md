# Integraciones y Plataforma: `crm-marketing`

Este documento describe la integración de `crm-marketing` con el API Gateway KrakenD, la sincronización de datos con el CRM y la interacción con el bus de eventos en Redis Streams y el motor de correos de `crm-media`.

---

## 1. Topología de Integración

```text
               ┌───────────────────────┐
               │    KrakenD Gateway    │
               └───────────┬───────────┘
                           │ (Peticiones Entrantes / X-User-Sub / X-User-Role)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                        crm-marketing                        │
│               (Módulos Marketing y Analytics)               │
└──────────────┬───────────────────────────────┬──────────────┘
               │                               │
               │ (Consulta Proyectos/Clientes) │ (Consumo de Eventos de Proyectos)
               ▼                               ▼
      ┌──────────────────┐            ┌──────────────────┐
      │    crm-collab    │            │  Redis Streams   │
      │  (Vía Gateway)   │            │ stream:collab... │
      └──────────────────┘            │      events      │
                                      └────────┬─────────┘
                                               │
                                               ▼
                                  [ CollabProjectStreamConsumer ]
                                   (Grupo: marketing-projector)
```

---

## 2. Integración con KrakenD API Gateway

- **Manifiesto del Servicio**: [`gateway/gateway.manifest.json`](../gateway/gateway.manifest.json).
- **Consolidación en Infra**: Durante el build de infraestructura, `crm-infra` lee el manifiesto de marketing y genera las más de 40 rutas correspondientes en `krakend.json`.
- **Rutas de Salud**:
  - `GET /api/v1/health` responde el estado del datasource y los hilos de ejecución de Spring.
  - KrakenD monitorea este endpoint aplicando disyuntores de circuito (*circuit breakers*) automáticos si se detectan 3 fallos consecutivos.

---

## 3. Consumo Asíncrono de Eventos en Redis Streams (`CollabProjectStreamConsumer`)

`crm-marketing` mantiene una vista materializada de proyectos en tiempo real consumiendo eventos publicados por `crm-collab`:

- **Stream Consumido**: `stream:collab.events` (definido canónicamente en `STREAM_CONVENTIONS`).
- **Grupo de Consumidores**: `marketing-projector` (configurable vía variable `COLLAB_PROJECTION_CONSUMER_GROUP`).
- **Tipos de Eventos Procesados**:
  - `project.created`: Inserta o actualiza la proyección del proyecto en la base de datos local (`schema_marketing.PROJECTS`).
  - `project.updated`: Sincroniza cambios de estado, fechas y cliente asignado.
- **Servicio de Proyección**: Gestionado por `ProjectProjectionService` para alimentar dashboards y reportes analíticos de manera reactiva e idempotente.

---

## 4. Sincronización Programada con el CRM (`CrmSyncService`)

Como mecanismo de respaldo y rehidratación masiva complementario a los eventos en tiempo real:

1. **Configuración de Sincronización**:
   - `cimaxis.crm.sync.enabled=true`: Habilita el sincronizador.
   - `cimaxis.crm.sync.on-startup=true`: Ejecuta una sincronización no bloqueante al iniciar. Si el CRM no responde, el servicio arranca con una advertencia en el log (*fail-safe*).
   - `cimaxis.crm.sync.cron=0 0 */6 * * *`: Tarea programada que refresca clientes y proyectos cada 6 horas.
2. **Canal de Consulta**: Invoca los endpoints públicos del CRM a través de `CRM_BASE_URL` (`http://localhost:28080`), poblando las tablas locales `CLIENTS` y `PROJECTS` en `schema_marketing`.
3. **Resiliencia HTTP y Timeouts**:
   - `CrmAuthClient`, `CrmClientClient` y `CrmProjectClient` operan sobre una instancia administrada de `RestTemplate` provista por `HttpClientConfig`.
   - Utiliza `JdkClientHttpRequestFactory` con connection pooling nativo de Java 21 (`HttpClient`).
   - Timeouts acotados configurables: `crm.client.connect-timeout-seconds` (3s por defecto) y `crm.client.read-timeout-seconds` (5s por defecto), evitando bloqueo indefinido de hilos Tomcat.

---

## 5. Tareas Programadas y Automatización (*Schedulers*)

El microservicio utiliza el planificador nativo de Spring (`@Scheduled`):

- **Consolidación de KPIs (`cimaxis.kpi.cron=0 30 1 * * *`)**:
  - Se ejecuta todas las noches a la 1:30 AM.
  - Calcula agregaciones de conversión, volumen de facturación y efectividad de campañas, guardando el registro histórico en `KPI_SNAPSHOTS`.
- **Ejecución de Workflows (`cimaxis.scheduler.cron=0 0 * * * *`)**:
  - Sondea cada hora reglas de inactividad comercial (ej. clientes sin contacto en 15 días) y dispara alertas operativas.
  - Gestiona reintentos con intervalo de 60 minutos y tope de 3 intentos fallidos.
