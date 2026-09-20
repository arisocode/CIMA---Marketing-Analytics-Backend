# Proyecto CIMA - Documentación Técnica

## Resumen del proyecto

CIMA---Marketing-Analytics-Backend es una aplicación backend en Java Spring Boot (Spring Boot 4, Java 21). Provee APIs REST para los módulos de Marketing y Analytics, integración con un CRM externo y persistencia en MySQL.

## Estructura general

- `src/main/java/com/cimaxis/demo/` — paquete raíz.
  - `config` — configuraciones (seguridad, CORS).
  - `security` — filtro JWT (`JwtAuthenticationFilter`).
  - `integration/crm` — clientes y servicio de integración con CRM.
  - `marketing` — módulo existente (controllers, services, repositories, domain).
  - `analytics` — nuevo módulo de reportes y KPIs (controller, service, repository, dto, domain).
- `src/main/resources/application.properties` — configuración de base de datos y CRM.

## Dependencias importantes

- Spring Boot Starters: `webmvc`, `data-jpa`, `security`, `validation`.
- MySQL Connector.
- Lombok para reducir boilerplate.

## Seguridad

- `JwtAuthenticationFilter` decodifica el payload del JWT y coloca `userId` en el `HttpServletRequest`.
- `SecurityConfig` establece `SessionCreationPolicy.STATELESS` y exige autenticación para todas las rutas excepto `/actuator/**`.

**Importante**: el filtro actual no valida firma de token — revisarlo si se requiere seguridad estricta.

## Base de datos

- `application.properties` contiene la URL del datasource. El esquema principal está descrito en `estructura_base.sql`.
- Repositorios usan Spring Data JPA y mapeos de entidades en `marketing` y `analytics`.

## Conveciones de código

- Capas: `controller` -> `service` -> `repository`.
- Nombres de tablas en mayúscula (por compatibilidad con el dump SQL).
- Entidades JPA usan Lombok (`@Getter`, `@Setter`, `@Builder`).

## Cómo compilar y ejecutar (local)

```bash
# Desde la raíz del repo
./mvnw.cmd -DskipTests package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## Pruebas

- Agregar tests unitarios en `src/test/java/...`
- Para pruebas de integración con DB: usar Testcontainers o una base de datos H2 con scripts de inicialización.

## Despliegue y operación

- Variables críticas: `spring.datasource.*`, `crm.base.url`, `server.port`.
- Considerar manejar secretos con Vault o variables de entorno, no en `application.properties` en repositorios.

## Buenas prácticas y recomendaciones

- Validar firmas JWT y manejar expiración.
- Usar DTOs para todas las respuestas públicas y no exponer entidades JPA directamente cuando haya campos sensibles.
- Implementar logging estructurado y monitoreo de endpoints críticos.
- Revisar políticas de CORS (hay `CorsConfig` y configuración en `SecurityConfig`). Mantener sincronía entre ellas.

## Dónde continuar

- Mejorar pruebas para `AnalyticsService`.
- Implementar paginación y filtros en endpoints de reports.
- Añadir exportación de reportes y endpoints scheduling para generación automática de `KPI_SNAPSHOTS`.

