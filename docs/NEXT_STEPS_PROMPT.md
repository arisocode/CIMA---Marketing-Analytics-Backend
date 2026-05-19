# Prompt para el próximo desarrollador / IA

Contexto: El proyecto contiene un módulo de Marketing y un nuevo módulo `analytics` para reportes. Queremos expandir analytics con más reportes, optimización y pruebas.

Objetivos inmediatos (prioridad alta):

1. Implementar paginación y filtros en endpoints que devuelven listas grandes (`/customers/activity`, `/kpis`, `/inventory/low-stock`).
2. Añadir endpoints para exportar CSV y XLSX de reportes clave (`summary`, `low-stock`, `campaigns performance`).
3. Implementar caching en `AnalyticsService` para `getSummary()` y `getKpiSnapshots()` con TTL configurable.
4. Añadir pruebas unitarias y de integración (usar H2 o Testcontainers MySQL). Cubrir `AnalyticsService` con mocks para repositorios.
5. Crear consultas optimizadas (native queries) para reportes pesados y documentarlas.

Requerimientos técnicos:

- Mantener compatibilidad con Spring Boot 4 y Java 21.
- Usar `Pageable` y `Page<T>` de Spring Data para paginación.
- Para exportación usar `Apache POI` o `OpenCSV` según el formato.
- Caching: `Caffeine` o `Redis` (decidir según infraestructura). Preferir una solución in-memory para prueba rápida.
- Tests: usar JUnit 5, Mockito, y Testcontainers para pruebas que toquen MySQL.

Tareas de ejemplo que puedes pedir a la IA/DEV (copy-paste):

- "Añade paginación a `AnalyticsController#getKpiSnapshots` usando `Pageable` y adapta `AnalyticsService` para aceptar filtros de fecha." 
- "Implementa un endpoint `GET /api/analytics/export/low-stock?format=csv` que devuelva CSV con columnas: product_id, product_name, total_stock, low_stock_alert." 
- "Añade caching con Caffeine en `AnalyticsService.getSummary()` con TTL 5 minutos y una llave dependiente de la fecha/hora de generación." 

Formatos de entrega esperados:

- PRs pequeños y atómicos (1 feature por PR).
- Tests incluidos con cada feature.
- Documentación actualizada en `docs/` para cada cambio.

Notas finales:

Si necesitas, ejecuta primero una compilación local rápida con `./mvnw.cmd -DskipTests package` y luego corre pruebas específicas con `./mvnw.cmd -Dtest=AnalyticsServiceTest test`.

Gracias — cualquier cambio, documenta en `docs/` y actualiza este prompt con nuevas prioridades.
