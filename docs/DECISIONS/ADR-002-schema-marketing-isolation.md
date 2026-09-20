# ADR-002: Segregación en PostgreSQL `schema_marketing` y Proyecciones Locales de Datos

- **Estado**: Aceptado
- **Fecha**: 2026-06-05
- **Autores**: Equipo de Base de Datos y Arquitectura CIMA

---

## Contexto y Planteamiento del Problema

El módulo de Marketing y Analítica necesita correlacionar campañas y propuestas con información de clientes y proyectos existentes en el CRM.

Si `crm-marketing` consultara directamente mediante sentencias `JOIN` SQL las tablas de otros microservicios (`schema_collab.projects`, `schema_auth.users`):
1. Se destruiría la autonomía del microservicio, generando acoplamiento a nivel de esquema físico.
2. Modificaciones o migraciones de base de datos en colaboración podrían romper de forma imprevista las consultas de marketing.
3. Consultas analíticas complejas y pesadas podrían degradar el rendimiento transaccional de los tableros Kanban y el chat.

---

## Alternativas Evaluadas

### Opción 1: Consultas Directas entre Esquemas (Cross-Schema SQL JOINs)
- **Descripción**: Otorgar permisos a `marketing_user` sobre `schema_collab` para hacer joins SQL.
- **Desventajas**: Viola los principios fundamentales de microservicios y genera fragilidad extrema.

### Opción 2: Llamadas HTTP Síncronas en Tiempo Real para Cada Consulta Analítica
- **Descripción**: Cada vez que se consulta un resumen analítico, hacer decenas de peticiones REST hacia `crm-collab` para obtener los datos de proyectos.
- **Desventajas**: Latencia inaceptable para dashboards (cientos de milisegundos) y sobrecarga en los servicios operacionales.

### Opción 3 (Elegida): Proyecciones Locales Desnormalizadas Sincronizadas Periódicamente
- **Descripción**: Mantener tablas de proyección local (`CLIENTS`, `PROJECTS`) dentro de `schema_marketing`. Sincronizarlas mediante un servicio en segundo plano (`CrmSyncService`) consumiendo los endpoints del CRM vía Gateway cada 6 horas y al inicio.

---

## Decisión

Adoptar la **Opción 3**:
1. Conectar la aplicación exclusivamente a `schema_marketing`.
2. Modelar `Client` y `Project` como entidades JPA locales dentro del esquema de marketing.
3. Ejecutar sincronizaciones no bloqueantes periódicas para mantener las proyecciones actualizadas.
4. Ejecutar todas las agregaciones de KPIs sobre las tablas locales de forma instantánea.

---

## Consecuencias

### Positivas
- **Aislamiento Total**: Cero dependencias relacionales con los esquemas de otros servicios.
- **Consultas Rápidas**: Las consultas de analítica se ejecutan localmente en milisegundos.
- **Tolerancia a Fallos**: Si el CRM está temporalmente fuera de línea, marketing sigue respondiendo reportes con los últimos datos sincronizados.

### Negativas
- **Consistencia Eventual**: Las proyecciones de analítica pueden tener un desfase de hasta 6 horas respecto a los cambios en vivo del CRM.
