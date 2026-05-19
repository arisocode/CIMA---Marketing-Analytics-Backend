# Módulo Analytics — Marketing & Reports

## Resumen

Este documento describe el nuevo módulo `analytics` añadido al proyecto CIMA---Marketing-Analytics-Backend. El módulo sigue la misma arquitectura por capas que el módulo de marketing: `domain`, `repository`, `service`, `controller` y `dto`.

Objetivo: generar reportes y KPIs a partir de la base de datos sobre clientes, campañas, productos, inventario, proyectos e interacciones de marketing.

---

## Mapeo entre entidades y tablas SQL

- `Client` -> `CLIENTS`
- `User` -> `USERS`
- `Product` -> `PRODUCTS`
- `Inventory` -> `INVENTORY`
- `Project` -> `PROJECTS`
- `KpiSnapshot` -> `KPI_SNAPSHOTS`

---

## Paquetes y archivos clave

- `com.cimaxis.demo.analytics.domain` — entidades JPA que reflejan tablas existentes.
- `com.cimaxis.demo.analytics.repository` — repositorios Spring Data JPA.
- `com.cimaxis.demo.analytics.service.AnalyticsService` — orquestador de consultas y construcción de DTOs.
- `com.cimaxis.demo.analytics.controller.AnalyticsController` — endpoints REST públicos.
- `com.cimaxis.demo.analytics.dto` — DTOs usados por los endpoints.

Archivos añadidos (resumen):
- `src/main/java/com/cimaxis/demo/analytics/domain/` : Client, User, Product, Inventory, Project, KpiSnapshot
- `src/main/java/com/cimaxis/demo/analytics/repository/` : ClientRepository, UserRepository, ProductRepository, InventoryRepository, ProjectRepository, KpiSnapshotRepository
- `src/main/java/com/cimaxis/demo/analytics/service/AnalyticsService.java`
- `src/main/java/com/cimaxis/demo/analytics/controller/AnalyticsController.java`
- `src/main/java/com/cimaxis/demo/analytics/dto/` : varios DTOs para summary, distribuciones, alerts, snapshots

---

## Endpoints disponibles

- `GET /api/analytics/summary` — Resumen general (clientes, usuarios, campañas, proyectos, inventario, KPIs).
- `GET /api/analytics/customers/plan-distribution` — Distribución de clientes por plan.
- `GET /api/analytics/customers/activity` — Actividad por cliente (nº campañas, proyectos).
- `GET /api/analytics/campaigns/status` — Conteo de campañas por estado.
- `GET /api/analytics/inventory/low-stock` — Items de inventario con stock por debajo del umbral.
- `GET /api/analytics/kpis` — Historial de snapshots desde `KPI_SNAPSHOTS`.

Ejemplo curl:

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8081/api/analytics/summary
```

Nota: el proyecto usa seguridad JWT y espera el token en `Authorization: Bearer ...`.

---

## Consultas SQL de ejemplo (para analizar y expandir)

1) Top clientes por número de campañas

```sql
SELECT c.client_id, COUNT(*) AS campaigns
FROM CAMPAIGNS cp
JOIN CLIENTS c ON cp.client_id = c.client_id
GROUP BY c.client_id
ORDER BY campaigns DESC
LIMIT 10;
```

2) Campañas por estado

```sql
SELECT status, COUNT(*) AS cnt
FROM CAMPAIGNS
GROUP BY status;
```

3) Productos con stock bajo

```sql
SELECT p.product_id, p.product_name, i.total_stock, i.low_stock_alert
FROM INVENTORY i
JOIN PRODUCTS p ON p.product_id = i.product_id
WHERE i.total_stock <= i.low_stock_alert;
```

4) Interacciones por campaña (rendimiento)

```sql
SELECT m.campaign_id, m.interaction_type, COUNT(*) AS cnt
FROM MARKETING_INTERACTIONS m
GROUP BY m.campaign_id, m.interaction_type;
```

5) Nuevos clientes por mes

```sql
SELECT DATE_FORMAT(created_at, '%Y-%m') AS period, COUNT(*) AS new_clients
FROM CLIENTS
GROUP BY period
ORDER BY period DESC;
```

6) KPIs históricos (leer `KPI_SNAPSHOTS` directamente)

```sql
SELECT period, calculated_at, new_clients, active_campaigns, response_rate
FROM KPI_SNAPSHOTS
ORDER BY calculated_at DESC
LIMIT 12;
```

---

## Diseño y recomendaciones técnicas

- Mantener la separación de capas (controller -> service -> repository). El `AnalyticsService` debe seguir siendo libre de detalles HTTP y concentrarse en reglas y composición de datos.
- Consultas costosas: usar paginación, caches y/o materialized views si el dataset crece.
- Para reportes complejos, preferir consultas nativas optimizadas en `@Query` o repositories dedicados con JDBC/NamedParameterJdbcTemplate para mejor control.
- Asegurar que los DTOs expuestos no filtren información sensible (ej. `password_hash`).
- Agregar pruebas unitarias para `AnalyticsService` y pruebas de integración que muevan datos ficticios a H2 o testcontainers MySQL.

---

## Pasos siguientes sugeridos

- Añadir paginación a endpoints que devuelvan listas grandes.
- Añadir filtros por rango de fechas y por cliente/proyecto.
- Implementar caché para endpoints de summary y KPIs con TTL (e.g., Caffeine o Redis).
- Añadir endpoints para exportar CSV/XLSX de reportes.
- Añadir métricas y trazabilidad (Actuator, Micrometer).

---

## Referencias

- Estructura de base de datos: `estructura_base.sql` (en la raíz del repo).
- Endpoints del módulo marketing: `src/main/java/com/cimaxis/demo/marketing/`.


