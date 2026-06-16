# Configurar secrets para CD de Marketing

Este documento explica como configurar GitHub Actions para que el repositorio de Marketing pueda desplegar el componente `marketing` usando el flujo centralizado de `crm-infra`.

## Repositorio

Configurar estos valores en:

`arisocode/CIMA---Marketing-Analytics-Backend`

Ruta en GitHub:

`Settings` -> `Secrets and variables` -> `Actions`

## Secrets requeridos

Crear los siguientes `Repository secrets`.

| Secret | Valor que debe tener | Notas |
| --- | --- | --- |
| `DEPLOY_SSH_HOST` | Host o IP publica del servidor de produccion | Debe ser el mismo host usado por `crm-infra` para desplegar. Ejemplo: `203.0.113.10` o `cima.example.com`. |
| `DEPLOY_SSH_PORT` | Puerto SSH del servidor | Normalmente `22`, salvo que el servidor use otro puerto. |
| `DEPLOY_SSH_USER` | Usuario Linux usado para desplegar | Debe tener permisos para entrar por SSH, usar Docker, leer/escribir en `DEPLOY_BASE_DIR` y ejecutar los scripts de despliegue. |
| `DEPLOY_SSH_PRIVATE_KEY` | Llave privada SSH completa | Pegar el contenido completo de la llave privada autorizada para `DEPLOY_SSH_USER`, incluyendo las lineas `BEGIN` y `END`. No usar la llave publica. |
| `DEPLOY_BASE_DIR` | Directorio base de despliegue en el servidor | Debe apuntar al directorio donde viven los repos de la plataforma. Valor esperado si se sigue el estandar actual: `/opt/cima`. |

Los valores deben coincidir con los secrets equivalentes del repositorio `SebasCarvajal11/crm-infra`. No crear un servidor, usuario o ruta diferente solo para Marketing, porque el despliegue es centralizado y comparte el mismo stack productivo.

## Variable para despliegue automatico

Crear tambien la siguiente `Repository variable`:

| Variable | Valor | Efecto |
| --- | --- | --- |
| `AUTO_DEPLOY_ENABLED` | `true` | Cuando el CI termina exitosamente en `main`, GitHub Actions dispara el despliegue automatico del componente `marketing`. |

Si se quiere dejar solo despliegue manual, crear la variable con valor `false` o no crearla. En ese caso, el workflow `CD` se puede ejecutar desde `Actions` -> `CD` -> `Run workflow`.

## Variables de entorno productivas en el servidor

Ademas de los secrets de GitHub, el servidor debe tener configurados los archivos `.env.production` reales. Estos archivos no se suben a Git.

### `crm-infra/.env.production`

Debe incluir `MARKETING_DB_PASSWORD` con la clave real de la base de datos de Marketing:

```env
MARKETING_DB_PASSWORD=<clave-real-marketing>
```

No usar valores placeholder como `change-me`, `marketingpassword`, `marketingpassword_ci`, `password` o `secret`.

### `crm-marketing/.env.production`

Crear desde `.env.production.example` y ajustar:

```env
PORT=3003
DATABASE_HOST=postgres_db
DATABASE_PORT=5432
DATABASE_NAME=crm_database
DB_SCHEMA=schema_marketing
DATABASE_USER=marketing_user
DATABASE_PASSWORD=<clave-real-marketing>
CRM_BASE_URL=http://api-gateway:8080
```

`DATABASE_PASSWORD` debe ser exactamente el mismo valor configurado en `crm-infra/.env.production` como `MARKETING_DB_PASSWORD`. El deploy falla a proposito si no coinciden.

## Checklist de verificacion

1. Confirmar que los cinco `Repository secrets` existen en GitHub Actions.
2. Confirmar que `AUTO_DEPLOY_ENABLED=true` si se quiere despliegue automatico.
3. Confirmar que la llave publica correspondiente a `DEPLOY_SSH_PRIVATE_KEY` esta en `~/.ssh/authorized_keys` del usuario `DEPLOY_SSH_USER`.
4. Confirmar que `DEPLOY_SSH_USER` puede ejecutar Docker en el servidor.
5. Confirmar que `DEPLOY_BASE_DIR` existe o puede ser creado por el usuario de deploy.
6. Confirmar que `crm-infra/.env.production` y `crm-marketing/.env.production` existen en el servidor con claves reales.
7. Ejecutar manualmente el workflow `CD` una primera vez desde GitHub Actions.
8. Despues del primer deploy manual exitoso, dejar activo el deploy automatico si el equipo lo necesita.

## Comportamiento esperado

Cuando alguien haga push o merge a `main`:

1. Corre el workflow `CI` de Marketing.
2. Si el CI termina exitosamente y `AUTO_DEPLOY_ENABLED=true`, corre el workflow `CD`.
3. El workflow `CD` llama al reusable deploy de `crm-infra`.
4. `crm-infra` actualiza solo el componente `marketing` dentro del stack productivo, usando despliegue blue/green.
