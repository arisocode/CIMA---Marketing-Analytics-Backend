# Guía de Agentes: `crm-marketing`

Este archivo es el **enrutador principal para Agentes de Inteligencia Artificial**. La documentación técnica y de negocio completa y detallada está estructurada en la carpeta [`docs/`](./docs/README.md).

---

## Misión del Servicio

`crm-marketing` es el **microservicio Java Spring Boot responsable de campañas publicitarias, propuestas comerciales, segmentación de audiencias, consolidación de KPIs y automatización de flujos de trabajo (*workflows*)** en CIMA CRM. Opera de forma aislada dentro de su propio esquema de base de datos (`schema_marketing`) y se expone exclusivamente a través del API Gateway KrakenD.

---

## Enrutamiento Documental para Agentes

Antes de proponer o ejecutar cambios, consulta el documento especializado correspondiente a tu objetivo:

| Si tu tarea involucra... | Consulta este documento |
| :--- | :--- |
| Comprender la arquitectura Java Spring Boot 4 / Java 21 y capas | [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) |
| Entender entidades de campañas, propuestas, analítica y workflows | [`docs/DOMAIN.md`](./docs/DOMAIN.md) |
| Modificar o auditar controladores REST y endpoints en KrakenD | [`docs/API.md`](./docs/API.md) |
| Modificar tablas en `schema_marketing`, entidades JPA o repositorios | [`docs/DATABASE.md`](./docs/DATABASE.md) |
| Ajustar el filtro de seguridad `JwtAuthenticationFilter` o roles | [`docs/SECURITY.md`](./docs/SECURITY.md) |
| Configurar sincronización con el CRM o tareas programadas cron | [`docs/INTEGRATIONS.md`](./docs/INTEGRATIONS.md) |
| Ejecutar o crear pruebas con JUnit 5, Mockito o Testcontainers | [`docs/TESTING.md`](./docs/TESTING.md) |
| Entender decisiones estructurales (Java 21, Spring Boot, Schemas, Schedulers) | [`docs/DECISIONS/`](./docs/DECISIONS/README.md) |

---

## Reglas Inviolables para Agentes de IA

1. **Gestor de Compilación Único**: Utilizar **exclusivamente `./mvnw` (Linux/macOS) o `mvnw.cmd` (Windows)**. Nunca asumir la presencia de Maven global.
2. **Aislamiento Estricto de Datos**: Todas las consultas SQL, entidades JPA y operaciones de persistencia deben ejecutarse exclusivamente sobre `schema_marketing`. Prohibido realizar `JOIN` o consultas a tablas de otros microservicios.
3. **Seguridad Delegada en el Gateway**: No reimplementar validaciones de tokens JWT de usuario en Java. Confiar en los encabezados sanitizados `X-User-Sub` y `X-User-Role` inyectados por KrakenD.
4. **Despacho de Correo Unificado**: Las notificaciones o campañas por correo deben enviarse a través del endpoint de `crm-media` (`POST /api/v1/emails/send`), nunca conectándose a servidores SMTP directamente.
5. **No Romper Contratos**: Cualquier nuevo endpoint público debe añadirse simultáneamente a `gateway/gateway.manifest.json` para que KrakenD pueda enrutarlo.
