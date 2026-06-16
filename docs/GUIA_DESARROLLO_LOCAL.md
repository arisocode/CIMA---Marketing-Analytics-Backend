# Guia rapida de desarrollo local

Esta guia es para trabajar en el modulo de Marketing sin levantar todos los microservicios del CRM.

## Repositorios

Clonar estos repos en la misma carpeta padre:

```text
workspace/
  crm-auth/
  crm-marketing/
  crm-frontend/
  crm-infra/
```

URLs:

```text
Backend Marketing:
https://github.com/arisocode/CIMA---Marketing-Analytics-Backend

Auth local:
https://github.com/SebasCarvajal11/crm-auth

Frontend compartido:
https://github.com/SebasCarvajal11/crm-frontend

Infra / Gateway / Docker local:
https://github.com/SebasCarvajal11/crm-infra
```

Nota: el repo de backend se puede clonar con cualquier nombre, pero para trabajar con `crm-infra` es mas simple dejarlo como `crm-marketing`.

## Requisitos

Instalar:

- Git
- Docker Desktop
- Java JDK 21
- Node.js 22
- pnpm 11

No usar `npm`. En este proyecto se usa `pnpm`.

No hace falta instalar Maven global. El backend usa Maven Wrapper:

```text
./mvnw
.\mvnw.cmd
```

## Que se levanta localmente

Para desarrollo diario de Marketing:

```text
crm-marketing   backend que modifica el equipo
crm-auth        login local del CRM
crm-frontend    frontend compartido
crm-infra       postgres, redis y api-gateway
```

No es necesario levantar todos los microservicios todos los dias.

## 1. Levantar infraestructura minima

Desde `crm-infra`:

```bash
cd crm-infra
cp .env.example .env
pnpm install
pnpm compose:generate
docker compose up -d postgres_db redis api-gateway
```

En Windows PowerShell:

```powershell
cd crm-infra
Copy-Item .env.example .env
pnpm install
pnpm compose:generate
docker compose up -d postgres_db redis api-gateway
```

Despues de copiar `.env.example`, dejar estas variables asi para este flujo:

```env
COMPOSE_PROJECT_NAME=crm_infra_local
GATEWAY_HOST_PORT=18080
POSTGRES_HOST_PORT=15432
REDIS_HOST_PORT=16379
AUTH_DB_PASSWORD=authpassword
MARKETING_DB_PASSWORD=marketingpassword
KRAKEND_AUTH_HOST=http://host.docker.internal:3000
KRAKEND_MARKETING_HOST=http://host.docker.internal:3003
```

`KRAKEND_AUTH_HOST` y `KRAKEND_MARKETING_HOST` apuntan desde el contenedor del gateway hacia los servicios levantados en la maquina local.

Esto deja disponible:

```text
PostgreSQL: localhost:15432
Redis:      localhost:16379
Gateway:    http://localhost:18080
```

## 2. Levantar Auth local

Marketing requiere estar autenticado en el CRM. Para evitar depender del servidor compartido, levantar `crm-auth` localmente.

Desde `crm-auth`:

```bash
cd crm-auth
cp .env.example .env
pnpm install
pnpm jwt:gen-keys
```

En Windows PowerShell:

```powershell
cd crm-auth
Copy-Item .env.example .env
pnpm install
pnpm jwt:gen-keys
```

El comando `pnpm jwt:gen-keys` imprime `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` y `JWT_KID`. Copiar esos valores dentro del archivo `.env` de `crm-auth`.

Para desarrollo local, dejar tambien:

```env
DATABASE_URL=postgres://auth_user:authpassword@localhost:15432/crm_database
DB_SCHEMA=schema_auth
NODE_ENV=development
EXPOSE_TEMP_PASSWORDS=false
REFRESH_COOKIE_PATH=/api/v1/auth/refresh
REDIS_URL=redis://127.0.0.1:16379
AUTH_EVENTS_STREAM_KEY=stream:auth.identity
AUTH_EVENTS_STREAM_MAXLEN=10000
PORT=3000
JWT_PRIVATE_KEY=<copiar desde pnpm jwt:gen-keys>
JWT_PUBLIC_KEY=<copiar desde pnpm jwt:gen-keys>
JWT_KID=mod-auth-rsa-1
APP_PUBLIC_URL=http://localhost:5173
MAIL_TRANSPORT=log
MAIL_FROM="CIMA CRM <noreply@example.com>"
ADMIN_INVITE_SECRET=local-admin-secret
```

No hace falta configurar SMTP real, Brevo, secretos productivos ni llaves JWT del servidor para desarrollo local.

Preparar base de datos y usuarios de prueba:

```bash
pnpm db:bootstrap
pnpm db:push
pnpm db:seed
pnpm dev
```

Usuario local de prueba:

```text
Email: admin@cima.dev
Clave: Admin123!
```

Auth queda disponible en:

```text
http://localhost:3000
```

## 3. Levantar backend de Marketing

Desde `crm-marketing`:

Linux/macOS:

```bash
cd crm-marketing
cp .env.example .env
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd crm-marketing
Copy-Item .env.example .env
.\mvnw.cmd spring-boot:run
```

El backend queda en:

```text
http://localhost:3003
```

El `.env` local esperado es:

```env
PORT=3003
DATABASE_HOST=localhost
DATABASE_PORT=15432
DATABASE_NAME=crm_database
DB_SCHEMA=schema_marketing
DATABASE_USER=marketing_user
DATABASE_PASSWORD=marketingpassword
CRM_BASE_URL=http://localhost:18080
```

## 4. Levantar frontend

Desde `crm-frontend`:

```bash
cd crm-frontend
cp .env.example .env
pnpm install
pnpm dev
```

En Windows PowerShell:

```powershell
cd crm-frontend
Copy-Item .env.example .env
pnpm install
pnpm dev
```

Abrir:

```text
http://localhost:5173
```

El frontend usa el gateway por proxy:

```env
VITE_API_BASE_URL=
VITE_API_PROXY_TARGET=http://localhost:18080
VITE_AUTH_API_VERSION=v1
VITE_COLLAB_API_VERSION=v1
VITE_MEDIA_API_VERSION=v1
```

## Autenticacion

Marketing requiere usuario autenticado del CRM. El flujo recomendado es usar `crm-auth` local y entrar desde el frontend con:

```text
Email: admin@cima.dev
Clave: Admin123!
```

Para desarrollo visual de componentes se puede avanzar dentro de `crm-frontend/src/features/marketing/` sin tener todo el stack completo, pero antes de abrir PR se debe validar el flujo con `crm-auth`, `crm-marketing`, `crm-frontend` y el gateway local.

## Donde trabajar en el frontend

Cada modulo tiene su carpeta propia dentro de `src/features`.

Marketing debe trabajar principalmente en:

```text
crm-frontend/src/features/marketing/
```

Estructura sugerida:

```text
api/      llamadas HTTP
hooks/    hooks de React Query
model/    tipos y schemas
ui/       componentes
lib/      helpers propios
utils/    funciones utilitarias
```

Evitar tocar codigo de otros modulos.

Archivos compartidos que se pueden tocar solo si hace falta:

```text
src/shared/lib/gateway-routes.ts
src/pages/dashboard/dashboard-page.tsx
src/routes/-dashboard.search.ts
```

## Reglas importantes

- No llamar `localhost:3003` desde React.
- No llamar servicios internos directamente desde el frontend.
- Todas las llamadas del frontend deben ir por `/api/...` via KrakenD.
- Si agregan endpoints en backend, actualizar `crm-marketing/gateway/gateway.manifest.json`.
- Si agregan rutas consumidas por frontend, actualizar `crm-frontend/src/shared/lib/gateway-routes.ts`.
- No subir `.env`, `.env.production`, passwords ni llaves.

## Comandos de validacion

Backend:

Linux/macOS:

```bash
./mvnw test
./mvnw clean package -DskipTests --no-transfer-progress
```

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests --no-transfer-progress
```

Frontend:

```bash
pnpm lint
pnpm build
pnpm audit:gateway-routes
```

Gateway, cuando cambien endpoints:

```bash
cd crm-infra
pnpm gateway:build
```

## Flujo de trabajo recomendado

1. Crear rama desde `main`.
2. Hacer cambios en `crm-marketing` y/o `crm-frontend`.
3. Mantener cambios de frontend dentro de `src/features/marketing/` siempre que se pueda.
4. Ejecutar validaciones locales.
5. Abrir PR.
6. Esperar CI verde.
7. Mergear a `main`.

El despliegue de Marketing ya esta conectado al CI/CD. Un push a `main` con CI exitoso despliega el componente `marketing`.
