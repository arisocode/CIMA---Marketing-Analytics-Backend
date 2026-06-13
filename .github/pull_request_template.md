## Objetivo

<!-- Describir que cambia y por que. -->

## Tipo de cambio

- [ ] Feature
- [ ] Fix
- [ ] Refactor
- [ ] Docs
- [ ] CI/CD
- [ ] Otro

## Checklist CIMA

- [ ] Ejecute `./mvnw test` localmente o confirme por que no aplica.
- [ ] El CI de GitHub Actions quedo en verde.
- [ ] Si agregue o cambie endpoints publicos, actualice `gateway/gateway.manifest.json`.
- [ ] Las rutas publicas mantienen el prefijo `/api/v1/...`.
- [ ] El backend no valida JWT directamente; usa headers del Gateway (`X-User-Sub`, `X-User-Role`).
- [ ] Los IDs externos de otros modulos del CRM se mantienen como `String`.
- [ ] Los cambios de base de datos respetan `schema_marketing`.
- [ ] No agregue secretos ni credenciales reales al repositorio.

## Impacto en integracion

<!-- Indicar si requiere cambios en crm-infra, crm-frontend, contratos, variables de entorno o despliegue. -->

## Evidencia

<!-- Pegar enlace del run de CI, capturas o comandos relevantes. -->
