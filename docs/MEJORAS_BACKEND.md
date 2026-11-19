# Mejoras implementadas en el backend SILBOM

**Proyecto:** SILBOM — Hito 2, Arquitectura de Desarrollo.

**Fecha:** 2 de noviembre de 2026.

**Rama:** `improvement/backend-hito2`.

## Objetivo del trabajo

Mejorar la seguridad, la lógica de bloqueos LOTO, la consistencia de los datos y
las pruebas del backend existente, conservando su arquitectura monolítica
multicapa y los registros de desarrollo.

SILBOM registra y supervisa procedimientos. Su estado digital no sustituye los
candados físicos, el aislamiento de energía ni la verificación de energía cero.

## 1. Bloqueos simultáneos y liberación

Se incorporaron bloqueos de filas y transacciones para coordinar las operaciones
de varios técnicos sobre una misma máquina.

- Las altas simultáneas mantienen el contador de bloqueos consistente.
- La máquina conserva el estado digital de bloqueo mientras quede un ticket activo.
- Liberar el mismo ticket por segunda vez devuelve `409 Conflict`, sin volver a
  descontar el contador.
- Las pruebas utilizan dos identidades y conexiones independientes a PostgreSQL.

## 2. Usuarios, roles y permisos

La clave compartida de la API se reemplazó por autenticación individual mediante
HTTP Basic y contraseñas almacenadas con BCrypt, con costo 12.

| Perfil | Operaciones permitidas |
|---|---|
| Público | Consultar el catálogo de máquinas |
| Técnico | Registrar sus bloqueos, consultar tickets de la planta y liberar sus propios tickets |
| Supervisor | Administrar máquinas y técnicos, consultar tickets y notas de la planta |

El responsable se obtiene de la identidad autenticada, no del nombre enviado en
el JSON. El supervisor tampoco puede liberar un bloqueo ajeno. Las notas
confidenciales solo se entregan al responsable o a un supervisor.

Se agregó el recurso `tecnicos`, con CRUD y baja lógica. Los DTO de respuesta no
exponen contraseñas ni hashes. Fuera del desarrollo local, HTTP Basic necesita
una conexión HTTPS configurada en el despliegue.

## 3. Historial y trazabilidad LOTO

- Los tickets activos y cerrados se conservan: su eliminación devuelve `409`.
- Se registran eventos `CREADO` y `LIBERADO` dentro de la misma transacción que
  modifica el ticket, con responsable y fecha.
- Los registros anteriores se identifican como `LEGADO_IMPORTADO`, sin inventar
  identidades ni transiciones pasadas.
- PostgreSQL protege los eventos contra modificaciones y borrado. Un trigger
  restringe las actualizaciones del ticket a la transición `ACTIVO → CERRADO`.
- Máquinas y técnicos usan baja lógica para preservar las referencias históricas.

## 4. Cálculo del MTBF y registro de fallas

Se corrigió el indicador para aplicar:

```text
MTBF = horas operativas / número de fallas
```

- Con cero fallas, devuelve `null`: el indicador no es calculable.
- Las intervenciones preventivas no incrementan las fallas.
- Una misma falla atendida por varios técnicos se cuenta una sola vez mediante
  `referenciaFalla`, compartida por máquina y planta.
- Una avería distinta debe tener otra referencia.
- Se rechazan horas negativas, valores no finitos y desbordamientos de contadores.
- Las horas no aumentan por el simple paso del tiempo de reloj.

El resultado se redondea a un decimal. Es una métrica basada en datos declarados,
no una predicción exacta de futuras fallas. Los conteos históricos se conservaron
sin deduplicarlos mediante suposiciones.

## 5. PostgreSQL y aislamiento entre plantas

Se corrigieron las políticas Row-Level Security que permitían consultar datos
de San Joaquín desde el contexto de otra planta.

- Las cinco tablas usan RLS: `maquinas`, `tecnicos`, `fallas`, `bloqueos_loto` y
  `eventos_loto`.
- La planta se fija mediante `SET LOCAL` en cada transacción y conexión.
- Se elimina también la política permisiva antigua de tickets.
- Se incorporaron claves foráneas compuestas con planta, restricciones e índices.
- El usuario de la aplicación no es propietario ni superusuario y carece de
  permisos para eliminar físicamente el historial.
- El esquema y la migración son transaccionales y repetibles, sin `DROP TABLE`.

La actualización de la base de desarrollo se realizó con respaldo previo y
conservó las seis máquinas y los tres tickets originales.

## 6. Validación y respuestas HTTP

Se reforzaron la validación de DTO, las reglas de dominio y la comprobación del
texto después de normalizarlo. También se limitaron las notas confidenciales y
se validó la referencia de falla.

Los errores distinguen entradas inválidas (`400`), falta de autenticación (`401`),
falta de permiso (`403`), recursos inexistentes (`404`), métodos no admitidos
(`405`), conflictos (`409`), contenido no soportado (`415`) y límites de
peticiones (`429`). Las respuestas no exponen SQL ni trazas internas.

Se probaron CORS, cabeceras de seguridad y el límite de peticiones. HSTS se envía
solo sobre conexiones seguras; esa cabecera no instala ni configura HTTPS.

## 7. Dependencias y documentación

- Spring Boot se actualizó de `3.3.5` a `3.5.16`, conservando Java 21 y la versión
  principal 3.x.
- Se actualizaron el README, el modelo de datos y la colección Postman.
- Se agregó una matriz que distingue controles implementados, comprobados y
  limitados.
- Se incorporó un workflow de GitHub Actions para las pruebas con PostgreSQL.
- Se documentaron instalación, variables, migración y cambios de contrato.

Se evaluó Flyway y se dejó pendiente su incorporación con un baseline explícito
para bases existentes. Esta versión utiliza SQL transaccional y repetible.

## 8. Resultados de las pruebas

| Validación ejecutada | Resultado |
|---|---|
| Dominio, Mockito, seguridad y API con H2 | 37 pruebas exitosas |
| Integración con PostgreSQL real | 7 pruebas exitosas |
| Total Maven | **44 pruebas; cero fallos, errores u omisiones** |
| Colección Postman mediante Newman | **24 peticiones y 30 comprobaciones sin fallos** |
| Migración de la base original de desarrollo | Datos originales conservados |
| Reinicio de los servicios | Datos originales y de demostración conservados |

Las pruebas de PostgreSQL comprueban concurrencia, liberación repetida, rollback,
persistencia, claves foráneas, restricciones, cifrado, permisos y RLS. La
ejecución remota del workflow de GitHub Actions todavía no está verificada.

## 9. Cambios que deben considerar los clientes

| Contrato anterior | Contrato actual |
|---|---|
| API key compartida | Credenciales individuales con HTTP Basic |
| Lectura pública de tickets | Lectura autenticada de tickets |
| Nombre del técnico enviado por el cliente | Responsable obtenido de la autenticación |
| Correctiva sin referencia compartida | `referenciaFalla` obligatoria para CORRECTIVA |
| MTBF numérico incluso sin fallas | `null` cuando no existen fallas |
| Borrado de tickets cerrados | Historial conservado; DELETE devuelve `409` |
| Eliminación de máquinas y técnicos | Baja lógica con referencias conservadas |

Estos cambios de comportamiento fueron autorizados antes de implementarse.

## 10. Pendientes

- Crear el Pull Request: la rama está publicada, pero la API de GitHub fue
  bloqueada por el proxy con respuesta `403`.
- Completar la consulta externa de vulnerabilidades y confirmar el soporte
  vigente. No se afirma que las dependencias estén libres de vulnerabilidades.
- Conectar el frontend, que sigue siendo un prototipo, e implementar QR/NFC real.
- Revisar CSRF antes de integrar autenticación Basic en el navegador.
- Modelar explícitamente el período de medición del MTBF.
- Definir recuperación de cuentas, rotación de claves, paginación y observabilidad.
- Acordar un procedimiento para tickets legados activos sin identidad autenticada.

## Documentos y cambios relacionados

- [Diagnóstico detallado, archivos y evaluación del Hito 2](auditoria-backend.md).
- [Matriz de controles de seguridad](controles-seguridad.md).
- [Instalación y contratos de la API](../README.md).
- [Colección Postman](SILBOM_Postman_Collection.json).
- [Comparación de los cambios publicados](https://github.com/Bvstivn-A/Silbom_Pruebas/compare/baseline%2Fsilbom-original...improvement%2Fbackend-hito2).

Los cambios se publicaron en tres commits: `3add8f6` (implementación), `32a45f1`
(pruebas y CI) y `824986c` (documentación y Postman). No se modificó `main` ni se
fusionó automáticamente el trabajo.
