# ADR-001: Adopción de Java 21 y Spring Boot para Procesamiento Analítico y Marketing

- **Estado**: Aceptado
- **Fecha**: 2026-05-18
- **Autores**: Equipo de Backend y Marketing CIMA

---

## Contexto y Planteamiento del Problema

El módulo de Marketing y Analítica de CIMA CRM requiere procesar grandes volúmenes de registros históricos, calcular agregaciones de métricas (ROI, tasas de conversión, distribuciones de clientes) y ejecutar tareas programadas de background de forma concurrente.

Aunque la mayoría de microservicios de CIMA CRM utilizan TypeScript y Node.js, para este dominio específico se evaluó si convenía mantener TypeScript o aprovechar la madurez de Java y el ecosistema Spring Boot.

---

## Alternativas Evaluadas

### Opción 1: Implementar en Node.js / TypeScript
- **Descripción**: Desarrollar el módulo como otro microservicio Node.js con Hono o Express.
- **Desventajas**: Node.js maneja tareas de cálculo intensivo (*CPU-bound*) de forma menos eficiente debido a su modelo de bucle de eventos monohilo; las librerías de generación de reportes y agregaciones en Node.js son menos maduras que en el ecosistema Java empresarial.

### Opción 2: Implementar en Python (FastAPI / Pandas)
- **Descripción**: Usar Python para analítica de datos.
- **Desventajas**: Añadiría otro lenguaje y runtime dinámico con gestión de entornos virtuales (`venv`), complicando la estandarización de despliegues en contenedores Docker.

### Opción 3 (Elegida): Java 21 (LTS) con Spring Boot
- **Descripción**: Java 21 ofrece excelente rendimiento, tipos fuertemente tipados, hilos virtuales (Project Loom) y la madurez de Spring Boot (Spring Data JPA, Spring Security, Spring Scheduling). La integración con el resto de la plataforma se realiza transparentemente a través del API Gateway KrakenD mediante HTTP REST.

---

## Decisión

Adoptar la **Opción 3**:
1. Construir `crm-marketing` utilizando Java 21 y Spring Boot.
2. Usar el wrapper de Maven (`./mvnw` / `mvnw.cmd`) para compilar de forma homogénea en cualquier entorno.
3. Exponer todos los endpoints bajo `/api/v1/marketing/*` y `/api/v1/analytics/*`, integrados al Gateway KrakenD como cualquier otro microservicio de la plataforma.

---

## Consecuencias

### Positivas
- **Rendimiento y Escalabilidad**: Ejecución multihilo robusta para tareas de agregación y reportes analíticos pesados.
- **Ecosistema Maduro**: Uso de Spring Data JPA con Hibernate para persistencia relacional confiable.
- **Transparencia Total**: Los clientes web y el API Gateway no perciben diferencias de tecnología; el contrato HTTP REST es idéntico al de los servicios Node.js.

### Negativas
- **Huella de Memoria Base**: El proceso JVM requiere un consumo base de memoria RAM superior (~250-350 MB) al de un microservicio ligero Node.js.
