# Flujo de integracion con CIMA

Este documento resume las reglas operativas para que el equipo de marketing desarrolle su microservicio sin salirse del flujo de plataforma.

## Responsabilidad del equipo de marketing

El equipo de marketing es responsable de:

- mantener el codigo del microservicio en este repositorio;
- conservar los endpoints publicos bajo `/api/v1/...`;
- mantener actualizado `gateway/gateway.manifest.json`;
- ejecutar y corregir `./mvnw test`;
- abrir PRs con cambios pequenos y revisables;
- coordinar cambios que impacten `crm-frontend`, `crm-infra` o contratos compartidos.

## Responsabilidad de plataforma CIMA

La plataforma CIMA provee:

- PostgreSQL central;
- esquema dedicado `schema_marketing`;
- API Gateway central;
- workflow reusable de CI;
- smoke tests full-stack;
- despliegue central por slots.

## Desarrollo local

Para probar solo el backend:

```bash
./mvnw test
```

Para correr el backend conectado al stack local:

```bash
cp .env.example .env
./mvnw spring-boot:run
```

Si el servicio necesita PostgreSQL, Redis o Gateway locales, levantarlos desde `crm-infra`:

```bash
docker compose up -d postgres_db redis api-gateway
```

## Cambios de API

Cada endpoint publico requiere dos cambios coordinados:

1. Controlador Spring Boot con ruta real.
2. Entrada equivalente en `gateway/gateway.manifest.json`.

Ejemplo:

```json
{
  "endpoint": "/api/v1/marketing/campaigns",
  "method": "GET",
  "backend_url": "/api/v1/marketing/campaigns",
  "openapi_ref": "GET /api/marketing/campaigns"
}
```

El `endpoint` es la ruta publica vista por frontend. El `backend_url` es la ruta interna del microservicio.

## Seguridad

El microservicio no debe validar JWT localmente. La identidad llega desde KrakenD mediante:

- `X-User-Sub`
- `X-User-Role`

Si un endpoint debe ser publico para infraestructura, como el manifest del Gateway, debe quedar documentado y permitido explicitamente en Spring Security.

## Base de datos

El servicio usa:

- base: `crm_database`;
- esquema: `schema_marketing`;
- rol: `marketing_user` en entornos integrados.

No asumir acceso a tablas de otros esquemas. Cualquier integracion con otros modulos debe hacerse por API o contrato aprobado.

## Reglas de PR

Antes de abrir PR:

```bash
./mvnw test
```

El PR debe explicar:

- que cambia;
- si afecta Gateway;
- si afecta frontend;
- si afecta esquema de base de datos;
- enlace o evidencia del CI.

## Produccion

Las credenciales reales se configuran como secretos de despliegue, no en este repositorio.

Antes de produccion estable se debe reemplazar `ddl-auto=update` por migraciones versionadas con Flyway o herramienta equivalente.
