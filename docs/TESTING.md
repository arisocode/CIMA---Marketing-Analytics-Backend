# Estrategia de Pruebas: `crm-marketing`

Este documento describe los niveles de prueba, las herramientas de automatización (JUnit 5, Mockito, Testcontainers) y los comandos de ejecución para `crm-marketing`.

---

## 1. Pirámide de Pruebas y Aislamiento

```text
       ▲
      / \     Nivel 3: Pruebas E2E / Smoke vía KrakenD (Desde crm-infra)
     /   \
    /─────\   Nivel 2: Integración de Base de Datos con Testcontainers (Postgres 16)
   /       \
  /─────────\ Nivel 1: Pruebas Unitarias Aisladas (JUnit 5 + Mockito)
```

### Nivel 1: Pruebas Unitarias (`AnalyticsServiceTest`, `WorkflowExecutionServiceTest`)
- **Herramientas**: JUnit 5 y Mockito.
- **Alcance**:
  - Cálculos matemáticos de agregación y métricas de analítica en `AnalyticsService`.
  - Evaluación de reglas condicionales, umbrales de días sin contacto y conteo de reintentos en `WorkflowExecutionService`.
  - Mapeo de entidades a DTOs.
- **Ejecución**: Pruebas en memoria ultrarrápidas con repositorios simulados (*mocks*).

### Nivel 2: Pruebas de Integración con Testcontainers
- **Herramienta**: Testcontainers con imagen oficial `postgres:16-alpine`.
- **Alcance**: Ejecuta pruebas de repositorios Spring Data JPA y controladores REST (`MarketingApiSecurityIntegrationTest`) contra una base de datos PostgreSQL real y efímera levantada automáticamente en Docker.
- **Ventaja**: No requiere configurar bases de datos locales ni limpiar tablas manualmente; cada ejecución es completamente estéril e independiente.

---

## 2. Catálogo de Comandos de Compilación y Prueba

En Windows (PowerShell / CMD):

```powershell
# Ejecutar la suite completa de pruebas
.\mvnw.cmd test

# Ejecutar una clase de prueba específica
.\mvnw.cmd -Dtest=AnalyticsServiceTest test

# Ejecutar pruebas de seguridad del gateway
.\mvnw.cmd -Dtest=MarketingApiSecurityIntegrationTest test

# Compilar el artefacto JAR omitiendo pruebas
.\mvnw.cmd -DskipTests package
```

En Linux / macOS:

```bash
./mvnw test
./mvnw -Dtest=AnalyticsServiceTest test
./mvnw -DskipTests package
```

---

## 3. Verificación de Contrato con el Gateway

Antes de fusionar cambios a la rama `main`:
1. Asegurar que cualquier nuevo endpoint declarado en los controladores de Spring Boot (`@GetMapping`, `@PostMapping`, etc.) esté registrado con la misma firma en `gateway/gateway.manifest.json`.
2. Validar que la compilación del Gateway en `crm-infra` (`pnpm gateway:build`) no reporte advertencias ni endpoints huérfanos.
