# CIMA Marketing Analytics Backend

Microservicio Java Spring Boot para los modulos de Marketing, Analytics y Reportes de CIMA.

Este repositorio pertenece al equipo de marketing, pero esta integrado al flujo de plataforma CIMA mediante:

- API Gateway central con KrakenD.
- Base de datos PostgreSQL central con esquema dedicado `schema_marketing`.
- CI reusable de `SebasCarvajal11/crm-infra`.
- Smoke tests full-stack desde `crm-infra`.
- Frontend central `crm-frontend`, que consume los endpoints publicados por el Gateway.

## Requisitos

- JDK 21.
- Docker Desktop o Docker Engine.
- Git.
- No se requiere Maven global; usar siempre `./mvnw` o `mvnw.cmd`.

## Configuracion local

1. Copiar el archivo de entorno:

   ```bash
   cp .env.example .env
   ```

2. Levantar la infraestructura central desde `crm-infra` cuando se quiera ejecutar el servicio conectado al stack:

   ```bash
   docker compose up -d postgres_db redis api-gateway
   ```

3. Ejecutar el servicio:

   En Linux/macOS:

   ```bash
   ./mvnw spring-boot:run
   ```

   En Windows:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

El servicio corre por defecto en `http://localhost:3003`.

## Pruebas y build

Los tests usan Testcontainers con PostgreSQL 16. Por eso no necesitan una base local previa, pero si requieren Docker activo.

En Linux/macOS:

```bash
./mvnw test
./mvnw -DskipTests package
docker build -t crm-marketing:local .
```

En Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
docker build -t crm-marketing:local .
```

## Contrato de integracion CIMA

- Todas las rutas publicas deben vivir bajo `/api/v1/...`.
- El backend no debe validar JWT directamente. El Gateway valida el token y propaga identidad mediante headers confiables.
- El servicio lee el usuario desde `X-User-Sub` y el rol desde `X-User-Role`.
- Los IDs externos que vienen de otros modulos del CRM deben modelarse como `String`.
- La conexion a base de datos debe mantenerse dentro de `schema_marketing`.
- Los clientes HTTP internos deben usar `CRM_BASE_URL` y pasar por el API Gateway.

## Gateway

Cuando se agregue, cambie o elimine un endpoint publico:

1. Crear o actualizar el controlador Spring Boot.
2. Actualizar `gateway/gateway.manifest.json`.
3. Verificar que el endpoint publico y el `backend_url` coincidan con la ruta real.
4. Ejecutar `./mvnw test`.
5. Abrir PR y esperar CI verde.

El manifest tambien se publica en runtime en:

```text
GET /api/v1/_gateway/gateway.manifest.json
```

Ese endpoint no requiere JWT para que `crm-infra` pueda generar configuracion del Gateway en CI y en despliegue.

## CI/CD

El workflow local `.github/workflows/ci.yml` invoca el reusable central:

```text
SebasCarvajal11/crm-infra/.github/workflows/reusable-ci.yml@v2.2.0
```

En cada push o pull request se valida:

- escaneo de secretos;
- build Maven;
- tests Maven;
- manifest de Gateway;
- OpenAPI cuando aplique;
- build de imagen Docker;
- escaneo Trivy;
- SBOM.

El CI central de `crm-infra` tambien clona este repositorio y valida el stack completo con smoke tests.

## Flujo de trabajo esperado

1. Crear una rama desde `main`.
2. Hacer cambios pequenos y enfocados.
3. Mantener actualizado `gateway/gateway.manifest.json` si cambia la API publica.
4. Ejecutar `./mvnw test` antes de abrir PR.
5. Abrir PR usando la plantilla del repositorio.
6. Esperar CI verde antes de mergear.

`main` debe protegerse desde GitHub con permisos de administrador para requerir PR y status checks antes de merge. Esta configuracion no vive en el codigo del repositorio.

## Migraciones de base de datos

Por ahora el servicio usa `spring.jpa.hibernate.ddl-auto=update` porque el modelo de datos aun esta en evolucion temprana.

Antes de produccion estable se debe migrar a Flyway o equivalente:

- crear migracion inicial;
- cambiar Hibernate a `ddl-auto=validate`;
- versionar cada cambio de esquema en PR;
- validar el flujo completo en CI central.
