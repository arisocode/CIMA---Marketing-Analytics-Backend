# Contratos de API: `crm-marketing`

Este documento cataloga los endpoints expuestos a través del API Gateway KrakenD para los módulos de Marketing y Analítica de CIMA CRM.

---

## 1. Contrato de Entrada del Gateway

Todas las peticiones públicas pasan a través de KrakenD. El microservicio confía en las cabeceras de identidad inyectadas por el Gateway:
- `X-User-Sub`: Identificador UUID del usuario autenticado.
- `X-User-Role`: Rol del usuario (`admin`, `worker`, `cliente`).

---

## 2. Endpoints del Módulo de Marketing (`/api/v1/marketing/*`)

### A. Campañas (`/campaigns`)
| Método | Ruta | Descripción | Roles |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/campaigns` | Lista todas las campañas publicitarias | `admin`, `worker` |
| `POST` | `/api/v1/marketing/campaigns` | Crea una nueva campaña | `admin`, `worker` |
| `GET` | `/api/v1/marketing/campaigns/{id}` | Obtiene el detalle de una campaña | Todos (autenticados) |
| `PUT` | `/api/v1/marketing/campaigns/{id}` | Actualiza información o presupuesto | `admin`, `worker` |
| `DELETE`| `/api/v1/marketing/campaigns/{id}` | Elimina una campaña existente | `admin` |
| `GET` | `/api/v1/marketing/campaigns/client/{clientId}` | Lista campañas de un cliente específico | `admin`, `worker`, `cliente` |

### B. Propuestas Comerciales (`/proposals`)
| Método | Ruta | Descripción | Roles |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/proposals` | Lista propuestas comerciales | `admin`, `worker` |
| `POST` | `/api/v1/marketing/proposals` | Registra una nueva propuesta | `admin`, `worker` |
| `GET` | `/api/v1/marketing/proposals/{id}` | Obtiene detalle de propuesta | Todos (autenticados) |
| `PUT` | `/api/v1/marketing/proposals/{id}` | Modifica los términos de una propuesta | `admin`, `worker` |
| `DELETE`| `/api/v1/marketing/proposals/{id}` | Elimina una propuesta | `admin` |
| `PATCH` | `/api/v1/marketing/proposals/{id}/status` | Cambia estado (`APPROVED`, `REJECTED`, etc.) | Todos (autorizados) |
| `GET` | `/api/v1/marketing/proposals/client/{clientId}` | Propuestas de un cliente | `admin`, `worker`, `cliente` |

### C. Interacciones y Audiencias
| Método | Ruta | Descripción | Roles |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/marketing/interactions` | Historial de puntos de contacto | `admin`, `worker` |
| `POST` | `/api/v1/marketing/interactions` | Registra nueva interacción | `admin`, `worker` |
| `GET` | `/api/v1/marketing/audiences` | Lista segmentos de audiencia | `admin`, `worker` |
| `POST` | `/api/v1/marketing/audiences` | Define un nuevo segmento | `admin`, `worker` |

---

## 3. Endpoints del Módulo de Analítica (`/api/v1/analytics/*`)

| Método | Ruta | Propósito Analítico |
| :--- | :--- | :--- |
| `GET` | `/api/v1/analytics/summary` | Resumen ejecutivo general (campañas activas, clientes, KPIs) |
| `GET` | `/api/v1/analytics/customers/plan-distribution` | Distribución porcentual de clientes por plan contratado |
| `GET` | `/api/v1/analytics/customers/activity` | Métricas de actividad y proyectos por cliente |
| `GET` | `/api/v1/analytics/campaigns/status` | Conteo y distribución de campañas según su estado |
| `GET` | `/api/v1/analytics/kpis` | Serie histórica de métricas e instantáneas (`KPI_SNAPSHOTS`) |

---

## 4. Diagnóstico de Salud

- `GET /api/v1/health` o `GET /actuator/health`: Retorna el estado operativo de la aplicación y la conexión con la base de datos PostgreSQL.
