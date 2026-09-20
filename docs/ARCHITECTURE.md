# Arquitectura del Sistema: `crm-marketing`

Este documento detalla la arquitectura de software basada en Java 21 y Spring Boot, la organización modular de paquetes y el ciclo de vida de peticiones en `crm-marketing`.

---

## 1. Visión General y Topología

`crm-marketing` opera como un microservicio backend de alto rendimiento diseñado para procesamiento intensivo de datos analíticos, automatización de marketing y generación de reportes:

```text
               ┌───────────────────────┐
               │    KrakenD Gateway    │
               └───────────┬───────────┘
                           │ (HTTP REST / X-User-Sub, X-User-Role)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                        crm-marketing                        │
│                   (Java 21 / Spring Boot)                   │
│                                                             │
│ ┌───────────────────────────┐ ┌───────────────────────────┐ │
│ │     Módulo Marketing      │ │     Módulo Analytics      │ │
│ │ (Campañas, Propuestas,    │ │ (KPIs, Distribuciones,    │ │
│ │  Audiencias, Workflows)   │ │  Snapshots, Reportes)     │ │
│ └─────────────┬─────────────┘ └─────────────┬─────────────┘ │
│               │                             │               │
│ ┌─────────────┴─────────────────────────────┴─────────────┐ │
│ │                Capa de Acceso a Datos                   │ │
│ │            (Spring Data JPA / Hibernate)                │ │
│ └───────────────────────────┬─────────────────────────────┘ │
└─────────────────────────────┼───────────────────────────────┘
                              │
                              ▼
               ┌─────────────────────────────┐
               │        PostgreSQL 16        │
               │   (schema_marketing)        │
               └─────────────────────────────┘
```

---

## 2. Organización Modular de Paquetes (`com.cimaxis.demo`)

La estructura de código sigue una estricta separación por responsabilidades:

- **`config`**: Configuraciones de Spring (seguridad web, CORS, mapeadores de beans).
- **`security`**:
  - `JwtAuthenticationFilter`: Filtro perimetral que extrae las cabeceras inyectadas por el Gateway (`X-User-Sub`, `X-User-Role`) y puebla el `SecurityContextHolder`.
- **`marketing`**:
  - `controller`: Controladores REST para campañas, propuestas, interacciones y audiencias.
  - `service`: Lógica transaccional de marketing y motor de ejecución de workflows (`WorkflowExecutionService`).
  - `repository`: Interfaces Spring Data JPA para entidades de marketing.
  - `domain`: Modelos de base de datos (`Campaign`, `Proposal`, `Interaction`, `Audience`, `Workflow`).
  - `dto`: Objetos de transferencia de datos con validaciones (`jakarta.validation`).
- **`analytics`**:
  - `controller`: Endpoints de resúmenes analíticos (`AnalyticsController`).
  - `service`: Algoritmos de agregación, cálculo de métricas y snapshots de KPIs (`AnalyticsService`).
  - `repository`: Consultas optimizadas de agregación SQL (`KpiSnapshotRepository`, etc.).
  - `domain`: Entidades de lectura y métricas (`KpiSnapshot`, `Client`, `Project`).
- **`integration/crm`**:
  - Clientes REST y tareas programadas de sincronización de proyectos y clientes desde `crm-collab` y `crm-auth`.

---

## 3. Patrón de Capas y Ciclo de Vida de Peticiones

Cada solicitud HTTP sigue un flujo unidireccional y predecible:

1. **Filtro de Seguridad (`OncePerRequestFilter`)**: Lee `X-User-Sub` y `X-User-Role`. Establece la autenticación como `ROLE_<role>` en Spring Security.
2. **Controlador (`@RestController`)**: Valida la estructura del JSON entrante con `@Valid` y delega inmediatamente al servicio.
3. **Servicio (`@Service`)**: Encapsula las reglas de negocio bajo demarcación transaccional (`@Transactional`).
4. **Repositorio (`@Repository`)**: Ejecuta operaciones JPA / Hibernate sobre PostgreSQL restringidas a `schema_marketing`.
5. **DTO y Respuesta**: Convierte las entidades JPA a DTOs inmutables para responder al cliente, evitando fugas de modelos de persistencia.
