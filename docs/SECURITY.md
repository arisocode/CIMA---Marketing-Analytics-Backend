# Seguridad y Control de Acceso: `crm-marketing`

Este documento describe el modelo de seguridad perimetral, la integración con Spring Security mediante encabezados confiables y las políticas de roles en `crm-marketing`.

---

## 1. Modelo de Confianza Perimetral (*Gateway Trust Contract*)

En la arquitectura de CIMA CRM, la validación criptográfica de tokens JWT de usuario se realiza exclusivamente en el borde de la red por el API Gateway KrakenD:

```text
[ Petición Externa ] ──► [ KrakenD Gateway ]
                                │
                                │ 1. Valida firma RS256 y vigencia del JWT.
                                │ 2. Inyecta X-User-Sub y X-User-Role.
                                ▼
                     [ crm-marketing:3003 ]
                                │
                                ▼
                   [ JwtAuthenticationFilter ]
                                │
                                ▼
                  [ SecurityContextHolder ]
                  (ROLE_admin, ROLE_worker, etc.)
```

---

## 2. Filtro de Seguridad: `JwtAuthenticationFilter`

Ubicado en `src/main/java/com/cimaxis/demo/security/JwtAuthenticationFilter.java`:

1. **Extracción de Encabezados**:
   - `X-User-Sub`: Identificador del usuario emisor.
   - `X-User-Role`: Rol asignado en la plataforma.
2. **Poblado de Contexto de Seguridad**:
   - Si ambos encabezados están presentes, crea un `UsernamePasswordAuthenticationToken` con la autoridad `ROLE_<gatewayRole>` y lo inyecta en el `SecurityContextHolder` de Spring Security.
   - Registra el atributo `userId` en el `HttpServletRequest` para que los controladores y servicios puedan identificar al actor de la operación.
3. **Peticiones sin Encabezados**:
   - Si las cabeceras no están presentes y la ruta no es pública, Spring Security rechaza la petición con `401 Unauthorized` o `403 Forbidden`.

---

## 3. Matriz de Autorización por Roles

| Rol en Gateway | Autoridad Spring Security | Permisos en Marketing y Analítica |
| :--- | :--- | :--- |
| **`admin`** | `ROLE_admin` | Acceso total: creación, modificación y eliminación de campañas, propuestas, flujos y reportes. |
| **`worker`** | `ROLE_worker` | Gestión operativa: creación de propuestas, registro de interacciones, consulta de campañas y KPIs. |
| **`client`** | `ROLE_client` | Acceso restringido: consulta exclusiva de campañas y propuestas vinculadas a su propio `clientId`. |

---

## 4. Comunicación Segura de Salida hacia `crm-media`

Cuando `crm-marketing` requiere despachar correos electrónicos (campañas masivas o alertas administrativas):
- No se conecta directamente a servidores SMTP.
- Invoca de forma interna el endpoint `POST /api/v1/emails/send` de `crm-media`, autenticándose como servicio mediante un Service JWT firmado con algoritmo `RS256`.
