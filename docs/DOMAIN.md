# Modelo de Dominio: `crm-marketing`

Este documento detalla las entidades de negocio, el ciclo de vida de campañas y propuestas comerciales, y los mecanismos de analítica y automatización de flujos de trabajo.

---

## 1. Dominio de Marketing

### A. Campañas Publicitarias (`Campaign`)
Representa una iniciativa de comunicación orientada a un cliente específico o a un segmento de mercado:
- **Estados de Campaña**: `DRAFT` $\rightarrow$ `ACTIVE` $\rightarrow$ `PAUSED` $\rightarrow$ `COMPLETED` (o `CANCELLED`).
- **Atributos Clave**: Identificador, nombre de campaña, cliente asociado (`clientId`), presupuesto asignado, fechas de inicio y culminación, canales (redes sociales, email, display).

### B. Propuestas Comerciales (`Proposal`)
Documento formal y económico presentado a un cliente para la contratación de servicios:
- **Ciclo de Vida**:
  ```text
  [ DRAFT ] ──► [ SENT ] ──┬──► [ APPROVED ]
                           ├──► [ REJECTED ]
                           └──► [ EXPIRED ]
  ```
- **Atributos Clave**: Título, cliente, valor económico, fecha de validez, enlace a documento adjunto y notas de negociación.

### C. Interacciones con Clientes (`Interaction`)
Registro cronológico de puntos de contacto entre el equipo comercial y el cliente (llamadas telefónicas, reuniones virtuales, correos electrónicos y notas de seguimiento).

### D. Segmentación de Audiencias (`Audience`)
Grupos de usuarios clasificados por criterios de comportamiento, tamaño de empresa o sector industrial para focalizar esfuerzos de campaña.

---

## 2. Dominio de Analítica y KPIs

El módulo de analítica transforma los datos operacionales en métricas accionables para la toma de decisiones gerenciales:

- **Instantáneas de KPIs (`KpiSnapshot`)**:
  - Registros inmutables generados periódicamente (diaria o mensualmente).
  - Métricas: Retorno sobre la Inversión (ROI), Tasa de Conversión de Propuestas, Volumen de Clientes Activos y Distribución por Planes de Servicio.
- **Proyecciones Locales de Clientes y Proyectos**:
  - `crm-marketing` mantiene tablas de proyección local (`CLIENTS`, `PROJECTS`) sincronizadas periódicamente desde el CRM.
  - Esto permite realizar consultas analíticas pesadas (`GROUP BY`, agregaciones de series de tiempo) mediante consultas SQL directas, sin degradar los microservicios transaccionales (`crm-collab`).

---

## 3. Automatización de Flujos de Trabajo (*Workflows*)

Permite definir reglas automáticas basadas en eventos y tiempo:

1. **Reglas de Inactividad**: Dispara alertas si un cliente no ha recibido contacto o seguimiento comercial en un período configurable (por defecto 15 días).
2. **Alertas a Administradores**: Envía notificaciones de alerta operativa ante eventos críticos (`notify_admin`).
3. **Resiliencia en Ejecución**: Los workflows aplican reintentos con intervalo configurable (`retry-delay-minutes=60`) y límite de reintentos (`max-delivery-attempts=3`), asegurando que ejecuciones exitosas nunca se reenvíen por duplicado.
