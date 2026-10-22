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
| `GET` | `/api/v1/marketing/campaigns/client/{clientId}` | `/api/v1/marketing/campaigns/client/{clientId}` | Campañas cuya audiencia actual incluye al cliente (suyas, de un segmento que lo contiene o de todos). |
| `GET` | `/api/v1/marketing/campaigns/{id}` | `/api/v1/marketing/campaigns/{id}` | Obtiene el detalle de una campaña por su identificador. |
| `PUT` | `/api/v1/marketing/campaigns/{id}` | `/api/v1/marketing/campaigns/{id}` | Actualiza información, metas o presupuesto de campaña. |
| `DELETE`| `/api/v1/marketing/campaigns/{id}` | `/api/v1/marketing/campaigns/{id}` | Elimina una campaña existente. |

Audiencia: `audienceType` = `CLIENT` (por defecto; exige `clientId`), `SEGMENT` (exige `segmentId` de un segmento guardado activo) o `ALL`. La respuesta agrega `audienceType`, `segmentId`, `segmentName` y `audienceSize` (clientes que hoy forman la audiencia). **`clientId` puede venir `null`** en campañas SEGMENT o ALL. Al editar sin `audienceType` se conserva la audiencia actual.

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
| `PATCH` | `/api/v1/marketing/proposals/{id}/project` | `/api/v1/marketing/proposals/{id}/project` | `{projectId}` vincula el proyecto de CIMAxios (solo propuestas `Approved`); `projectId: null` desvincula. `ProposalResponse` incluye `projectId`. |

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
| `GET` | `/api/v1/marketing/clients` | `/api/v1/marketing/clients` | Lista de clientes sincronizados para marketing, con `score` (puntaje de interés 0–100). `/sin-plan` y `/plan/{plan}` también lo traen. |
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
| `GET` | `/api/v1/marketing/segments/saved` | `/api/v1/marketing/segments/saved` | Segmentos guardados `[{segmentId, name, description, criteria, active, clientCount}]`. |
| `POST` | `/api/v1/marketing/segments/saved` | `/api/v1/marketing/segments/saved` | Crea `{name, description, criteria}` (criteria = el mismo objeto de `/preview`). |
| `PUT` | `/api/v1/marketing/segments/saved/{segmentId}` | `/api/v1/marketing/segments/saved/{segmentId}` | Edita el segmento. |
| `PATCH` | `/api/v1/marketing/segments/saved/{segmentId}/active` | `/api/v1/marketing/segments/saved/{segmentId}/active` | `{active}`. 409 si una campaña activa lo usa. |
| `GET` | `/api/v1/marketing/segments/saved/{segmentId}/clients` | `/api/v1/marketing/segments/saved/{segmentId}/clients` | `[{clientId, name, plan, lastContact, score}]`. |
| `GET` | `/api/v1/marketing/workflows` | `/api/v1/marketing/workflows` | Lista todos los flujos de trabajo automatizados. |
| `POST` | `/api/v1/marketing/workflows` | `/api/v1/marketing/workflows` | Crea una nueva definición de workflow. |
| `GET` | `/api/v1/marketing/workflows/active` | `/api/v1/marketing/workflows/active` | Consulta workflows actualmente activos. |
| `GET` | `/api/v1/marketing/workflows/campaign/{campaignId}` | `/api/v1/marketing/workflows/campaign/{campaignId}` | Workflows configurados para una campaña. |
| `GET` | `/api/v1/marketing/workflows/{id}` | `/api/v1/marketing/workflows/{id}` | Detalle y configuración de un workflow. |
| `PUT` | `/api/v1/marketing/workflows/{id}` | `/api/v1/marketing/workflows/{id}` | Modifica la configuración de un workflow. |
| `DELETE`| `/api/v1/marketing/workflows/{id}` | `/api/v1/marketing/workflows/{id}` | Elimina un workflow. |
| `PATCH` | `/api/v1/marketing/workflows/{id}/toggle` | `/api/v1/marketing/workflows/{id}/toggle` | Activa o pausa la ejecución de un workflow. |
| `GET` | `/api/v1/marketing/workflows/{id}/preview` | `/api/v1/marketing/workflows/{id}/preview` | `{campaignName, campaignActive, audienceType, audienceSize, nextRunAt, eligible[{clientId,name,plan}], skipped[{clientId,name,reason}]}`. |
| `POST` | `/api/v1/marketing/workflows/{id}/run-now` | `/api/v1/marketing/workflows/{id}/run-now` | Evalúa solo ese workflow con las reglas del planificador: `{executed[{clientId,name,result,deliveryStatus,detail}], skipped[...]}`. |
| `POST` | `/api/v1/marketing/executions/run/{workflowId}` | `/api/v1/marketing/executions/run/{workflowId}` | Dispara la ejecución manual de un workflow. |
| `POST` | `/api/v1/marketing/executions/run/{workflowId}/client/{clientId}` | `/api/v1/marketing/executions/run/{workflowId}/client/{clientId}` | Ejecuta workflow para un cliente específico. |
| `GET` | `/api/v1/marketing/executions/workflow/{workflowId}` | `/api/v1/marketing/executions/workflow/{workflowId}` | Historial de ejecuciones de un workflow. |
| `GET` | `/api/v1/marketing/executions/client/{clientId}` | `/api/v1/marketing/executions/client/{clientId}` | Historial de ejecuciones recibidas por un cliente. |
| `POST` | `/api/v1/marketing/scheduler/run` | `/api/v1/marketing/scheduler/run` | Dispara el ciclo del scheduler bajo demanda. |

Disparadores (`WorkflowEligibilityService`, el mismo servicio para el planificador, la vista previa y "ejecutar ahora"). Todos actúan solo sobre la audiencia de la campaña y solo si la campaña está `Active` y hoy está entre sus fechas:
- `scheduled_date`: exige `scheduledAt` (fecha y hora de Bogotá); actúa cuando ahora ≥ `scheduledAt`, una vez por cliente.
- `no_contact_x_days`: último contacto hace más de `noContactDays` días (o nunca). Se repite si el último envío exitoso de ese workflow tiene más de N días.
- `proposal_no_response`: propuesta Sent o In_negotiation sin respuesta creada hace N días o más. Se repite igual.
- `project_completed`: algún proyecto completado; una vez por cliente.
- `manual`: el planificador nunca lo corre; solo "ejecutar ahora" (toda la audiencia) o un segmento.
- `noContactDays` solo se acepta en `no_contact_x_days` y `proposal_no_response`. Se mantienen los reintentos (máx. 3 fallos, espera de 60 min) y el cupo diario de correo.

Acciones: `send_email`, `send_whatsapp` (queda como tarea manual), `log_followup`, `notify_admin` y **`send_portal_message`** (publica un mensaje OUTBOUND de "Equipo CIMA" en el portal del cliente).

### E2. WhatsApp por enviar (admin y worker)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/whatsapp/pending` | `/api/v1/marketing/whatsapp/pending` | `[{interactionId, clientId, clientName, message, createdAt, workflowName}]` (channel whatsapp, SIMULATED). |
| `PATCH` | `/api/v1/marketing/whatsapp/pending/{interactionId}/sent` | `/api/v1/marketing/whatsapp/pending/{interactionId}/sent` | Marca `SENT_MANUAL` (desde ahí cuenta como contacto). 409 si no es un WhatsApp pendiente. |

`GET /api/v1/marketing/today` agrega `whatsappPending` (máx. 20) y `counts.whatsappPending`.

---

### F. Catálogo de Planes (`/plans`)
Lectura: `admin` y `worker`. Escritura: solo `admin`. Un plan no se borra: se desactiva, y un plan inactivo no se puede asignar a clientes.

| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/plans?includeInactive=false` | `/api/v1/marketing/plans` | Planes ordenados por `sortOrder`, con `clientCount`. |
| `POST` | `/api/v1/marketing/plans` | `/api/v1/marketing/plans` | Crea un plan `{name, color, description, sortOrder}`. |
| `PUT` | `/api/v1/marketing/plans/{planId}` | `/api/v1/marketing/plans/{planId}` | Edita el plan. Si cambia el nombre, actualiza en la misma transacción a los clientes que lo tienen. |
| `PATCH` | `/api/v1/marketing/plans/{planId}/active` | `/api/v1/marketing/plans/{planId}/active` | Activa o desactiva `{active}`. |

Respuesta: `{planId, name, color, description, sortOrder, active, clientCount}`. `color` es hexadecimal (`#RRGGBB`). La asignación de planes a clientes, la segmentación por plan y `GET /api/v1/analytics/customers/plan-distribution` (que ahora incluye `color`) se validan contra este catálogo.

### G. Correos (`/notifications`)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/notifications/email-quota` | `/api/v1/marketing/notifications/email-quota` | Cupo diario: `{enabled, dailyLimit, sentToday, remaining}` (zona America/Bogota). |
| `POST` | `/api/v1/marketing/notifications/test-email` | `/api/v1/marketing/notifications/test-email` | Solo `admin`. Envía un correo de prueba `{to}` con la plantilla de marca y devuelve `{deliveryStatus, detail}`. |

Los correos salen por `crm-media` (`POST /api/v1/emails/send`, JWT de servicio). Valores de `deliveryStatus`:
- `QUEUED`: crm-media respondió 202 y encoló el correo.
- `FAILED`: no se pudo entregar a crm-media.
- `SIMULATED`: el envío real está apagado (`CIMAXIS_MAIL_ENABLED=false`).
- `SKIPPED_LIMIT`: se alcanzó `CIMAXIS_MAIL_DAILY_LIMIT` y no se llamó a crm-media.

Las interacciones exponen `deliveryStatus` y `deliveryError`.

### H. Plantillas de Mensajes (`/templates`)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/templates?channel=&plan=` | `/api/v1/marketing/templates` | Lista; con `plan` incluye también las plantillas generales (sin plan). |
| `POST` | `/api/v1/marketing/templates` | `/api/v1/marketing/templates` | Crea `{name, channel, plan, subject, body}`; `channel` ∈ `EMAIL`, `WHATSAPP`, `IN_APP`. |
| `PUT` | `/api/v1/marketing/templates/{templateId}` | `/api/v1/marketing/templates/{templateId}` | Edita la plantilla. |
| `PATCH` | `/api/v1/marketing/templates/{templateId}/active` | `/api/v1/marketing/templates/{templateId}/active` | Activa o desactiva `{active}`. |
| `POST` | `/api/v1/marketing/templates/{templateId}/preview` | `/api/v1/marketing/templates/{templateId}/preview` | `{clientId}` → `{subject, body}` con las variables reemplazadas. |

Variables: `{nombre}`, `{plan}`, `{empresa}` y `{workflow}`. Un workflow acepta `templateId` opcional; si lo tiene y la plantilla está activa, el mensaje y el asunto del correo salen de ella.

### I. Flujo Comercial (admin y worker)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/funnel?period=YYYY-MM` | `/api/v1/marketing/funnel` | Embudo `{period, stages[{key,label,count}], conversionPercent[]}`. Sin `period` = acumulado histórico. |
| `GET` | `/api/v1/marketing/funnel/{stageKey}/clients?period=` | `/api/v1/marketing/funnel/{stageKey}/clients` | Clientes de la etapa `[{clientId,name,plan,lastContact,score}]`, de mayor a menor puntaje. |
| `GET` | `/api/v1/marketing/clients/{clientId}/timeline` | `/api/v1/marketing/clients/{clientId}/timeline` | Ficha 360°: `{client, stats, events}`; eventos `PLAN`, `CAMPAIGN`, `MESSAGE`, `RESPONSE`, `PROPOSAL`, `PROPOSAL_DECISION`, `PROJECT` en orden descendente. 404 si no existe. |
| `GET` | `/api/v1/marketing/today?daysWithoutContact=15&daysWithoutResponse=7` | `/api/v1/marketing/today` | Panel Hoy: clientes sin contacto, propuestas sin respuesta y aprobadas sin proyecto (máx. 20 por lista; `counts` con el total). |

Etapas del embudo (`stageKey`): `clients`, `contacted`, `responded`, `proposal_sent`, `approved`, `project`. Contacto = interacción que no es `SKIPPED_LIMIT`; respuesta = `KpiCalculationService.esRespuesta`. Con `period`, contactados y respondieron usan las mismas interacciones del mes que el KPI, así que coinciden con `clientsContacted` y `responseRate`. `conversionPercent[i] = count[i+1] / count[i] × 100` (null si `count[i]` es 0).

Puntaje de interés (`InterestScore`): +30 si respondió en los últimos 30 días, +20 con propuesta abierta (`Sent`/`In_negotiation`), +30 con propuesta `Approved`, +20 con proyecto, −1 por cada día sin contacto por encima de 15; acotado a 0–100.

### J. Portal del Cliente (solo rol `client`; el cliente sale de `X-User-Sub`)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/me/summary` | `/api/v1/marketing/me/summary` | `{clientName, plan, planColor, pendingProposals, unreadMessages, approvedProposals}`. |
| `GET` | `/api/v1/marketing/me/proposals` | `/api/v1/marketing/me/proposals` | `[{proposalId, title, description, value, status, documentUrl, createdDate, responseDate, clientComment, projectId}]`; solo Sent, In_negotiation, Approved y Rejected; pendientes primero. |
| `PATCH` | `/api/v1/marketing/me/proposals/{id}/decision` | `/api/v1/marketing/me/proposals/{id}/decision` | `{decision: ACCEPTED\|REJECTED, comment}`. 409 si ya no está en Sent/In_negotiation; 404 si no es suya. |
| `GET` | `/api/v1/marketing/me/messages` | `/api/v1/marketing/me/messages` | Conversación ascendente; marca como leídos los mensajes de CIMA. |
| `POST` | `/api/v1/marketing/me/messages` | `/api/v1/marketing/me/messages` | `{body}` (máx. 4000). Máximo 30 por hora (429). |

### K. Conversaciones Comerciales (admin y worker)
| Método | Endpoint Público KrakenD | Backend Interno | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/messages/threads` | `/api/v1/marketing/messages/threads` | `[{clientId, clientName, plan, lastMessage, lastAt, lastDirection, unread}]`; no leídos primero. |
| `GET` | `/api/v1/marketing/messages/{clientId}` | `/api/v1/marketing/messages/{clientId}` | Conversación del cliente (no cambia la lectura). |
| `POST` | `/api/v1/marketing/messages/{clientId}` | `/api/v1/marketing/messages/{clientId}` | `{body, alsoEmail}` → mensaje OUTBOUND con `emailDeliveryStatus` y `emailDetail` si se pidió correo. |
| `PATCH` | `/api/v1/marketing/messages/{clientId}/read` | `/api/v1/marketing/messages/{clientId}/read` | Marca como leídos los mensajes del cliente → `{marked}`. |

Cada mensaje y cada decisión del portal queda como interacción con `channel = "portal"` y `campaignId = null`: los del cliente como `inquiry` con `response` (cuentan como respuesta), los de CIMA como `message`. **`campaignId` de las interacciones puede venir `null`.** Cuando CIMA pasa una propuesta a `Sent`, el cliente recibe el correo "Tienes una nueva propuesta de CIMA" (respeta el cupo diario).

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
| `GET` | `/api/v1/analytics/export/kpis?format=xlsx\|pdf&period=` | `/api/v1/analytics/export/kpis` | Reporte de indicadores: resumen del último período con variación, historial y definiciones (Excel incluye hoja "Definiciones"). |
| `GET` | `/api/v1/analytics/export/campaigns?format=xlsx\|pdf` | `/api/v1/analytics/export/campaigns` | Reporte de campañas: resumen por estado y tipo, y una fila por campaña con el nombre del cliente. |
| `GET` | `/api/v1/analytics/export/low-stock?format=xlsx\|pdf` | `/api/v1/analytics/export/low-stock` | Alertas de inventario. |
| `GET` | `/api/v1/analytics/kpis/{kpi}/detail?period=` | `/api/v1/analytics/kpis/{kpi}/detail` | Registros que componen un KPI del período (por defecto el mes actual). |
| `GET` | `/api/v1/analytics/kpis/compare?period=` | `/api/v1/analytics/kpis/compare` | Los ocho KPIs del período frente al mes anterior (`deltaPercent` es `null` si el anterior es 0). |
| `GET` | `/api/v1/analytics/projects?status=&clientId=` | `/api/v1/analytics/projects` | Proyectos de la proyección local con su cliente y el valor aprobado del cliente. |
| `GET` | `/api/v1/analytics/export/kpis/{kpi}/detail?period=&format=` | `/api/v1/analytics/export/kpis/{kpi}/detail` | Exporta el detalle de un KPI en `xlsx` o `pdf`. |

Formato de los reportes descargables (`analytics/report`): PDF A4 horizontal con banda de marca CIMA (logo de `cimaxis.mail.logo-url` o el texto "CIMA"), pie "Generado por CIMAxis · Página X de Y" y la página "No hay datos para el período seleccionado" cuando no hay datos. Valores en español de Colombia (`ReportFormatter`): `$ 4.500.000`, `26,67 %`, `19,0 días`, `22 de octubre de 2026, 4:36 p. m.`, `Octubre 2026`, tipos y estados traducidos. En Excel los números son reales: moneda `$ #,##0`, porcentaje `0.00 %` (se guarda 0,2667), enteros `0`, días `0.0`; encabezado inmovilizado y autofiltro.

**Detalle de KPIs.** `{kpi}` ∈ `new_clients`, `closed_projects`, `revenue`, `active_campaigns`, `clients_contacted`, `response_rate`, `avg_close_days`, `projects_in_progress`. La respuesta es `{ kpi, period, total, kpiValue, items: [{ id, title, subtitle, date, value, status, clientId, clientName }] }`:
- `total` es el número de registros listados y `kpiValue` es el valor de la tarjeta.
- En los KPI de conteo los dos coinciden.
- En `revenue`, `kpiValue` es la suma de `value`.
- En `response_rate`, `kpiValue` es `total` entre los clientes contactados × 100.
- En `avg_close_days`, `kpiValue` es el promedio de `value`.

El detalle y el KPI salen de la misma selección (`KpiCalculationService.select`), así que siempre coinciden.

---

## 5. Diagnóstico de Salud

- `GET /api/v1/health` o `GET /actuator/health`: Retorna el estado operativo del microservicio y la conexión a PostgreSQL.
