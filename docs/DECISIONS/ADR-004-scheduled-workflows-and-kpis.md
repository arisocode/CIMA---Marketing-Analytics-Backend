# ADR-004: Automatización de Workflows y Consolidación de KPIs mediante Schedulers Nativos

- **Estado**: Aceptado
- **Fecha**: 2026-07-12
- **Autores**: Equipo de Ingeniería y Marketing CIMA

---

## Contexto y Planteamiento del Problema

`crm-marketing` debe realizar dos tareas periódicas esenciales:
1. Consolidar instantáneas históricas de métricas clave (KPIs de conversión y ROI) para evitar cálculos en tiempo real en los paneles de analítica.
2. Ejecutar flujos de trabajo automatizados (*workflows*), tales como alertar a administradores sobre clientes sin contacto en 15 días.

Se requería un mecanismo de programación periódica de tareas que fuera predecible, resiliente y de bajo costo de mantenimiento.

---

## Alternativas Evaluadas

### Opción 1: Cron Jobs del Sistema Operativo en el Servidor (Linux Crontab)
- **Descripción**: Configurar crontabs en el host para ejecutar comandos `curl` contra los endpoints de la API.
- **Desventajas**: Difícil de versionar y gestionar en contenedores Docker efímeros; expone endpoints que no deberían invocarse manualmente.

### Opción 2: Framework Pesado de Orquestación (Quartz Scheduler o Apache Airflow)
- **Descripción**: Implementar Quartz con persistencia de trabajos en base de datos o levantar un clúster Airflow.
- **Desventajas**: Complejidad y sobrecarga operativa desproporcionada para el volumen actual de tareas del servicio.

### Opción 3 (Elegida): Schedulers Nativos de Spring Boot (`@Scheduled`)
- **Descripción**: Utilizar el planificador nativo de Spring Boot mediante la anotación `@Scheduled` acoplada a expresiones cron configurables externamente en `application.properties`.

---

## Decisión

Adoptar la **Opción 3**:
1. Habilitar `@EnableScheduling` en la aplicación Spring Boot.
2. Definir expresiones cron en `application.properties`:
   - Consolidación de KPIs: `cimaxis.kpi.cron=0 30 1 * * *` (diario a la 1:30 AM).
   - Ejecución de workflows: `cimaxis.scheduler.cron=0 0 * * * *` (cada hora).
3. Implementar lógica de reintentos seguros (`retry-delay-minutes=60`, `max-delivery-attempts=3`) en `WorkflowExecutionService` para evitar duplicación de notificaciones.

---

## Consecuencias

### Positivas
- **Simplicidad y Robustez**: Gestión de tareas autocontenida en la aplicación sin dependencias de infraestructura externa.
- **Configurabilidad Dinámica**: Los intervalos y horarios pueden ajustarse por variables de entorno sin modificar el código fuente.
- **Idempotencia Garantizada**: Las tareas registran el estado de ejecución para evitar procesamientos duplicados.

### Negativas
- **Escalamiento Monoinstancia**: En un despliegue con múltiples réplicas del contenedor sin un lock distribuido (ShedLock), la tarea se ejecutaría en cada réplica. En la arquitectura actual de slot único en producción, esto no representa un problema.
