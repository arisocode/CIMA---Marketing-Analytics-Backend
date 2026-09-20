# Documentación Técnica: `crm-marketing`

Bienvenido a la documentación oficial del microservicio de Marketing, Analítica y Reportes de **CIMA CRM** (`crm-marketing`). Desarrollado en **Java 21 y Spring Boot**, este servicio administra las campañas publicitarias, propuestas comerciales, audiencias segmentadas, consolidación periódica de KPIs y automatización de flujos de trabajo (*workflows*).

---

## Índice de Documentación

| Documento | Audiencia Principal | Descripción |
| :--- | :--- | :--- |
| [**ARCHITECTURE.md**](./ARCHITECTURE.md) | Arquitectos / Backend | Diseño en capas Spring Boot, paquetes `marketing` y `analytics`, ciclo de vida. |
| [**DOMAIN.md**](./DOMAIN.md) | Negocio / Backend | Campañas, propuestas, interacciones, audiencias, snapshots de KPIs y workflows. |
| [**API.md**](./API.md) | Frontend / Integraciones | Catálogo de endpoints en KrakenD (`/api/v1/marketing/*`, `/api/v1/analytics/*`). |
| [**DATABASE.md**](./DATABASE.md) | DBA / Backend | Esquema PostgreSQL `schema_marketing`, entidades Spring Data JPA y proyecciones. |
| [**SECURITY.md**](./SECURITY.md) | Seguridad / DevOps | Contrato de confianza perimetral, `JwtAuthenticationFilter` y roles Spring Security. |
| [**INTEGRATIONS.md**](./INTEGRATIONS.md) | Plataforma / DevOps | KrakenD Gateway, sincronización de clientes con CRM, correo vía `crm-media`. |
| [**TESTING.md**](./TESTING.md) | QA / Desarrolladores | Pruebas JUnit 5, Mockito, Testcontainers PostgreSQL y comandos con `./mvnw`. |
| [**DECISIONS/**](./DECISIONS/README.md) | Todo el equipo | Architecture Decision Records (ADRs) que fundamentan el diseño del servicio. |

---

## Guía Rápida de Navegación para Agentes de IA

Si eres un **agente autónomo**, consulta directamente el archivo correspondiente a tu objetivo:

- **Modificar o agregar controladores y endpoints REST**: Consulta [`API.md`](./API.md) y [`ARCHITECTURE.md`](./ARCHITECTURE.md).
- **Alterar entidades JPA, repositorios o tablas en `schema_marketing`**: Consulta [`DATABASE.md`](./DATABASE.md).
- **Comprender reglas de campañas, propuestas o cálculo de KPIs**: Consulta [`DOMAIN.md`](./DOMAIN.md).
- **Ajustar filtros de seguridad o mapeo de roles `X-User-Role`**: Consulta [`SECURITY.md`](./SECURITY.md).
- **Configurar tareas programadas cron o sincronización externa**: Consulta [`INTEGRATIONS.md`](./INTEGRATIONS.md).
- **Ejecutar o escribir pruebas con Maven**: Consulta [`TESTING.md`](./TESTING.md).
- **Consultar fundamentos arquitectónicos**: Consulta [`DECISIONS/`](./DECISIONS/README.md).

---

## Reglas Inviolables del Repositorio

1. **Gestor de Compilación Único**: Utilizar **exclusivamente `./mvnw` (Linux/macOS) o `mvnw.cmd` (Windows)**. No requerir Maven global en el sistema.
2. **Aislamiento de Base de Datos**: Todas las consultas SQL y entidades deben operar exclusivamente dentro de `schema_marketing`. Prohibido realizar consultas a otros esquemas de microservicios.
3. **Seguridad Delegada en el Gateway**: El microservicio no valida firmas JWT directamente; confía en las cabeceras `X-User-Sub` y `X-User-Role` inyectadas por KrakenD.
4. **Despacho de Correos Centralizado**: La emisión de correos de marketing o notificaciones debe canalizarse a través del motor unificado de `crm-media` (`POST /api/v1/emails/send`).
