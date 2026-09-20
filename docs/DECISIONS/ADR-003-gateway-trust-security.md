# ADR-003: Modelo de Confianza Perimetral y Propagación de Identidad por Encabezados

- **Estado**: Aceptado
- **Fecha**: 2026-06-22
- **Autores**: Equipo de Seguridad y Plataforma CIMA

---

## Contexto y Planteamiento del Problema

Cada petición dirigida a `crm-marketing` proviene de un usuario autenticado en la plataforma web o móvil.

Implementar la validación completa del token JWT en el servicio Java implicaría:
1. Configurar un cliente JWKS adicional en Spring Security para descargar y validar llaves públicas de `crm-auth`.
2. Duplicar el cómputo criptográfico de validación de firma que el API Gateway KrakenD ya realiza al recibir la petición en el perímetro de la red.
3. Mayor latencia en cada endpoint debido a la doble verificación de tokens.

---

## Alternativas Evaluadas

### Opción 1: Doble Validación Criptográfica en Java (OAuth2 Resource Server)
- **Descripción**: Configurar Spring Security como un Resource Server OAuth2 completo que valida el token Bearer contra el JWKS de `crm-auth`.
- **Desventajas**: Sobrecarga innecesaria de CPU y latencia; duplicación de lo que KrakenD ya resuelve en el borde.

### Opción 2: Red Interna sin Autenticación
- **Descripción**: Permitir todas las peticiones que lleguen al puerto 3003 sin verificar identidad ni roles.
- **Desventajas**: Cualquier servicio interno o contenedor podría invocar acciones privilegiadas sin control de roles.

### Opción 3 (Elegida): Contrato de Confianza Perimetral con Encabezados Sanitizados
- **Descripción**: KrakenD valida el JWT y su firma RS256, descarta cualquier encabezado `X-User-*` proveniente del exterior y añade las cabeceras confiables `X-User-Sub` y `X-User-Role`. Un filtro ligero de Spring Security (`JwtAuthenticationFilter`) lee estas cabeceras y establece la autenticación y roles en `SecurityContextHolder`.

---

## Decisión

Adoptar la **Opción 3**:
1. Confiar en la validación perimetral ejecutada por KrakenD.
2. Implementar `JwtAuthenticationFilter` en `crm-marketing` que lee `X-User-Sub` y `X-User-Role`.
3. Mapear el rol a una autoridad de Spring Security (`ROLE_admin`, `ROLE_worker`, `ROLE_cliente`).
4. Aplicar control de acceso mediante anotaciones estándar de Spring (`@PreAuthorize`, `@Secured`) o configuración de rutas.

---

## Consecuencias

### Positivas
- **Máximo Rendimiento**: Cero sobrecarga de validación criptográfica en el proceso Java.
- **Simplicidad de Código**: Configuración de Spring Security limpia y desacoplada de llaves públicas RSA.
- **Control de Acceso Unificado**: Los roles son consistentes en toda la plataforma CIMA CRM.

### Negativas
- **Dependencia del Gateway**: El servicio asume que solo el Gateway o llamadas autenticadas de la red interna tienen acceso al puerto de escucha.
