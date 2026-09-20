# CRM Marketing Service

> Microservicio Java Spring Boot para Marketing, Analítica de Clientes y Reportes en CIMA CRM.

[![Status](https://img.shields.io/badge/status-active-success.svg)]()
[![Platform](https://img.shields.io/badge/platform-CIMA%20CRM-blue.svg)]()
[![Java](https://img.shields.io/badge/java-21%20LTS-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/spring--boot-3%2F4-brightgreen.svg)]()
[![License](https://img.shields.io/badge/license-MIT-blue.svg)]()

---

## Propósito

`crm-marketing` administra las campañas publicitarias, propuestas comerciales, segmentación de audiencias, cálculo y consolidación periódica de métricas de rendimiento (KPIs) y automatización de flujos de trabajo de seguimiento comercial (*workflows*). Se conecta a PostgreSQL bajo el esquema aislado `schema_marketing` y expone sus contratos a través del API Gateway KrakenD.

---

## Documentación Detallada (`docs/`)

Para consultar las especificaciones técnicas completas y guías de arquitectura, visita la suite documental:

- [**Arquitectura del Sistema (`docs/ARCHITECTURE.md`)**](./docs/ARCHITECTURE.md): Diseño en capas Spring Boot, paquetes `marketing` y `analytics`.
- [**Modelo de Dominio (`docs/DOMAIN.md`)**](./docs/DOMAIN.md): Campañas, propuestas, audiencias, snapshots de KPIs y flujos de trabajo.
- [**Contratos de API (`docs/API.md`)**](./docs/API.md): Endpoints públicos en KrakenD (`/api/v1/marketing/*`, `/api/v1/analytics/*`).
- [**Base de Datos y Persistencia (`docs/DATABASE.md`)**](./docs/DATABASE.md): Esquema PostgreSQL `schema_marketing`, entidades JPA y proyecciones.
- [**Seguridad y Control de Acceso (`docs/SECURITY.md`)**](./docs/SECURITY.md): Modelo de confianza perimetral, `JwtAuthenticationFilter` y roles.
- [**Integraciones y Plataforma (`docs/INTEGRATIONS.md`)**](./docs/INTEGRATIONS.md): KrakenD Gateway, sincronización de clientes y correo vía `crm-media`.
- [**Estrategia de Pruebas (`docs/TESTING.md`)**](./docs/TESTING.md): Pruebas JUnit 5, Mockito, Testcontainers y comandos con `./mvnw`.
- [**Decisiones Arquitectónicas (`docs/DECISIONS/`)**](./docs/DECISIONS/README.md): Registros formales de decisiones (ADRs).

---

## Inicio Rápido Local

### 1. Configuración de Entorno
```bash
cp .env.example .env
# Configurar contraseñas locales o ejecutar pnpm setup:env desde crm-infra
```

### 2. Ejecutar la Aplicación
En Windows (PowerShell / CMD):
```powershell
.\mvnw.cmd spring-boot:run
```

En Linux / macOS:
```bash
./mvnw spring-boot:run
```

El servicio inicia por defecto en `http://localhost:3003`.

---

## Pruebas y Validación de Calidad

```powershell
# Ejecutar todas las pruebas con Testcontainers PostgreSQL
.\mvnw.cmd test

# Compilar el paquete JAR omitiendo tests
.\mvnw.cmd -DskipTests package
```

---

## Despliegue en Producción

El despliegue está automatizado mediante GitHub Actions y orquestado por el script canónico de slots Blue/Green:

```bash
# Desde crm-infra/
./deploy/remote/deploy-component.sh marketing
```
