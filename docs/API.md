# Contratos de API: `crm-marketing`

Este documento cataloga los endpoints expuestos a través del API Gateway KrakenD para los módulos de Marketing y Analítica de CIMA CRM, alineados con [`gateway/gateway.manifest.json`](../gateway/gateway.manifest.json).

---

## 1. Contrato de Entrada del Gateway y Seguridad Perimetral

Todas las peticiones públicas pasan a través de KrakenD (`http://localhost:28080`). Conforme a **ADR-003**:
- El microservicio valida la cabecera compartida `X-Gateway-Secret` o la firma JWT portadora (`Authorization: Bearer <token>`).
- KrakenD propaga e inyecta la identidad verificada:
  - `X-User-Sub`: Identificador UUID del usuario autenticado.
  - `X-User-Role`: Rol canónico CIMA (`admin`, `worker`, `client`).

---

## 2. Endpoints del Módulo de Marketing (`/api/v1/marketing/*`)

### A. Campañas (`/campaigns`)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/campaigns` | `/api/v1/marketing/campaigns` | Lista todas las campañas comerciales. |
| `POST` | `/api/v1/marketing/campaigns` | `/api/v1/marketing/campaigns` | Crea una nueva campaña publicitaria. |
| `GET` | `/api/v1/marketing/campaigns/client/{clientId}` | `/api/v1/marketing/campaigns/client/{clientId}` | Lista campañas asociadas a un cliente específico. |
| `GET` | `/api/v1/marketing/campaigns/{id}` | `/api/v1/marketing/campaigns/{id}` | Obtiene el detalle de una campaña por su identificador. |
| `PUT` | `/api/v1/marketing/campaigns/{id}` | `/api/v1/marketing/campaigns/{id}` | Actualiza información, metas o presupuesto de campaña. |
| `DELETE`| `/api/v1/marketing/campaigns/{id}` | `/api/v1/marketing/campaigns/{id}` | Elimina una campaña existente. |

### B. Propuestas Comerciales (`/proposals`)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/proposals` | `/api/v1/marketing/proposals` | Lista propuestas comerciales registradas. |
| `POST` | `/api/v1/marketing/proposals` | `/api/v1/marketing/proposals` | Registra una nueva propuesta comercial. |
| `GET` | `/api/v1/marketing/proposals/client/{clientId}` | `/api/v1/marketing/proposals/client/{clientId}` | Lista propuestas dirigidas a un cliente. |
| `GET` | `/api/v1/marketing/proposals/status/{status}` | `/api/v1/marketing/proposals/status/{status}` | Filtra propuestas según su estado. |
| `GET` | `/api/v1/marketing/proposals/{id}` | `/api/v1/marketing/proposals/{id}` | Obtiene el detalle completo de una propuesta. |
| `PUT` | `/api/v1/marketing/proposals/{id}` | `/api/v1/marketing/proposals/{id}` | Modifica los términos o valores de una propuesta. |
| `DELETE`| `/api/v1/marketing/proposals/{id}` | `/api/v1/marketing/proposals/{id}` | Elimina una propuesta comercial. |
| `PATCH` | `/api/v1/marketing/proposals/{id}/status` | `/api/v1/marketing/proposals/{id}/status` | Actualiza el estado de la propuesta (`ACCEPTED`, `DECLINED`, etc.). |

### C. Interacciones (`/interactions`)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/interactions` | `/api/v1/marketing/interactions` | Historial general de interacciones con prospectos y clientes. |
| `POST` | `/api/v1/marketing/interactions` | `/api/v1/marketing/interactions` | Registra una nueva interacción (llamada, correo, reunión). |
| `GET` | `/api/v1/marketing/interactions/campaign/{campaignId}` | `/api/v1/marketing/interactions/campaign/{campaignId}` | Interacciones asociadas a una campaña. |
| `GET` | `/api/v1/marketing/interactions/client/{clientId}` | `/api/v1/marketing/interactions/client/{clientId}` | Interacciones sostenidas con un cliente. |
| `GET` | `/api/v1/marketing/interactions/{id}` | `/api/v1/marketing/interactions/{id}` | Detalle de una interacción particular. |
| `DELETE`| `/api/v1/marketing/interactions/{id}` | `/api/v1/marketing/interactions/{id}` | Elimina el registro de una interacción. |
| `PATCH` | `/api/v1/marketing/interactions/{id}/response` | `/api/v1/marketing/interactions/{id}/response` | Actualiza la respuesta o resultado de la interacción. |

### D. Gestión de Clientes y Planes (`/clients`)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/clients` | `/api/v1/marketing/clients` | Lista de clientes sincronizados para marketing. |
| `GET` | `/api/v1/marketing/clients/sin-plan` | `/api/v1/marketing/clients/sin-plan` | Clientes activos sin un plan asignado. |
| `GET` | `/api/v1/marketing/clients/plan/{plan}` | `/api/v1/marketing/clients/plan/{plan}` | Filtra clientes por categoría de plan contratado. |
| `PATCH` | `/api/v1/marketing/clients/plans` | `/api/v1/marketing/clients/plans` | Asignación masiva de planes a clientes. |
| `GET` | `/api/v1/marketing/clients/{clientId}` | `/api/v1/marketing/clients/{clientId}` | Consulta el estado y plan de un cliente. |
| `PATCH` | `/api/v1/marketing/clients/{clientId}/plan` | `/api/v1/marketing/clients/{clientId}/plan` | Actualiza el plan individual de un cliente. |

### E. Segmentación y Workflows (`/segments` y `/workflows`)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/marketing/segments/preview` | `/api/v1/marketing/segments/preview` | Previsualiza el recuento y perfiles del segmento según reglas. |
| `POST` | `/api/v1/marketing/segments/{workflowId}/execute` | `/api/v1/marketing/segments/{workflowId}/execute` | Ejecuta un workflow sobre un segmento definido. |
| `GET` | `/api/v1/marketing/workflows` | `/api/v1/marketing/workflows` | Lista todos los flujos de trabajo automatizados. |
| `POST` | `/api/v1/marketing/workflows` | `/api/v1/marketing/workflows` | Crea una nueva definición de workflow. |
| `GET` | `/api/v1/marketing/workflows/active` | `/api/v1/marketing/workflows/active` | Consulta workflows actualmente activos. |
| `GET` | `/api/v1/marketing/workflows/campaign/{campaignId}` | `/api/v1/marketing/workflows/campaign/{campaignId}` | Workflows configurados para una campaña. |
| `GET` | `/api/v1/marketing/workflows/{id}` | `/api/v1/marketing/workflows/{id}` | Detalle y configuración de un workflow. |
| `PUT` | `/api/v1/marketing/workflows/{id}` | `/api/v1/marketing/workflows/{id}` | Modifica la configuración de un workflow. |
| `DELETE`| `/api/v1/marketing/workflows/{id}` | `/api/v1/marketing/workflows/{id}` | Elimina un workflow. |
| `PATCH` | `/api/v1/marketing/workflows/{id}/toggle` | `/api/v1/marketing/workflows/{id}/toggle` | Activa o pausa la ejecución de un workflow. |
| `POST` | `/api/v1/marketing/executions/run/{workflowId}` | `/api/v1/marketing/executions/run/{workflowId}` | Dispara la ejecución manual de un workflow. |
| `POST` | `/api/v1/marketing/executions/run/{workflowId}/client/{clientId}` | `/api/v1/marketing/executions/run/{workflowId}/client/{clientId}` | Ejecuta workflow para un cliente específico. |
| `GET` | `/api/v1/marketing/executions/workflow/{workflowId}` | `/api/v1/marketing/executions/workflow/{workflowId}` | Historial de ejecuciones de un workflow. |
| `GET` | `/api/v1/marketing/executions/client/{clientId}` | `/api/v1/marketing/executions/client/{clientId}` | Historial de ejecuciones recibidas por un cliente. |
| `POST` | `/api/v1/marketing/scheduler/run` | `/api/v1/marketing/scheduler/run` | Dispara el ciclo del scheduler bajo demanda. |

---

## 3. Endpoints de Integración CRM (`/api/v1/integration/*`)

| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/integration/clients` | `/api/v1/integration/clients` | Clientes proyectados desde el CRM. |
| `GET` | `/api/v1/integration/projects` | `/api/v1/integration/projects` | Proyectos proyectados desde `crm-collab`. |
| `POST` | `/api/v1/integration/sync` | `/api/v1/integration/sync` | Fuerza la sincronización HTTP con servicios upstream. |
| `GET` | `/api/v1/integration/clients/{clientId}` | `/api/v1/integration/clients/{clientId}` | Detalle de integración de un cliente. |
| `GET` | `/api/v1/integration/clients/{clientId}/projects` | `/api/v1/integration/clients/{clientId}/projects` | Proyectos asociados al cliente. |
| `GET` | `/api/v1/integration/clients/{clientId}/overview` | `/api/v1/integration/clients/{clientId}/overview` | Vista consolidada cliente-proyecto. |

---

## 4. Endpoints del Módulo de Analítica (`/api/v1/analytics/*`)

| Método | Endpoint Público KrakenD | Backend Interno | Propósito Analítico |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/analytics/summary` | `/api/v1/analytics/summary` | Resumen ejecutivo general (campañas, clientes, proyectos). |
| `GET` | `/api/v1/analytics/customers/plan-distribution` | `/api/v1/analytics/customers/plan-distribution` | Distribución de clientes por plan contratado. |
| `GET` | `/api/v1/analytics/customers/activity` | `/api/v1/analytics/customers/activity` | Métricas de actividad y proyectos por cliente. |
| `GET` | `/api/v1/analytics/campaigns/status` | `/api/v1/analytics/campaigns/status` | Conteo y distribución de campañas según su estado. |
| `GET` | `/api/v1/analytics/inventory/low-stock` | `/api/v1/analytics/inventory/low-stock` | Reporte de inventario con stock crítico. |
| `GET` | `/api/v1/analytics/kpis` | `/api/v1/analytics/kpis` | Serie de métricas e instantáneas (`KpiSnapshotDto`). |
| `GET` | `/api/v1/analytics/kpis/current` | `/api/v1/analytics/kpis/current` | Snapshot de KPIs del período actual. |
| `GET` | `/api/v1/analytics/kpis/history` | `/api/v1/analytics/kpis/history` | Historial de snapshots consolidados. |
| `POST` | `/api/v1/analytics/kpis/calculate` | `/api/v1/analytics/kpis/calculate` | Fuerza el recálculo inmediato de los KPIs actuales. |
| `GET` | `/api/v1/analytics/kpis/period/{period}` | `/api/v1/analytics/kpis/period/{period}` | KPIs para un período específico (ej. `2026-09`). |
| `GET` | `/api/v1/analytics/kpis/current/{period}`| `/api/v1/analytics/kpis/current/{period}` | KPI actual para un período. |
| `POST` | `/api/v1/analytics/kpis/calculate/{period}`| `/api/v1/analytics/kpis/calculate/{period}` | Recálculo de KPIs para un período específico. |
| `GET` | `/api/v1/analytics/export/kpis` | `/api/v1/analytics/export/kpis` | Exportación CSV/Excel de KPIs. |
| `GET` | `/api/v1/analytics/export/campaigns` | `/api/v1/analytics/export/campaigns` | Exportación de métricas de campañas. |
| `GET` | `/api/v1/analytics/export/low-stock` | `/api/v1/analytics/export/low-stock` | Exportación de reporte de inventario bajo. |

---

## 5. Diagnóstico de Salud

- `GET /api/v1/health` o `GET /actuator/health`: Retorna el estado operativo del microservicio y la conexión a PostgreSQL.
