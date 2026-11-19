# Auditoría del backend SILBOM — Hito 2

Fecha: 2026-11-02. Base: importación original `a31b017`, conservada en
`baseline/silbom-original`. Rama de trabajo: `improvement/backend-hito2`.

## Alcance y evidencia inicial

Monolito Java 21 / Spring Boot 3.3.5. Existen controller, service, repository,
model, DTO, exception, security y config. Los controladores delegan al servicio;
las respuestas usan DTOs y las consultas de negocio están parametrizadas.
No se detectaron ciclos de dependencias en estos componentes.

Implementado: máquinas, tickets LOTO, carga inicial, validación de entradas,
API key compartida, cifrado AES-GCM, filtros HTTP y almacenamiento PostgreSQL.
No implementado: usuarios autenticados individualmente, roles efectivos,
incidentes compartidos, auditoría inmutable, migraciones y frontend conectado.
El HTML es un prototipo con estado en memoria y NFC simulado.

Comprobado antes de cambiar código: `mvn package`, 7 pruebas H2, cero errores,
fallos u omisiones; flujo HTTP sobre PostgreSQL 17; persistencia tras reinicio.
Eso no demuestra concurrencia real, autorización por técnico, aislamiento RLS
ni todas las garantías declaradas en el README original.

## Diagnóstico inicial

| Prioridad | Ubicación original | Problema | Evidencia / corrección prevista |
|---|---|---|---|
| CRÍTICO | `service/BloqueoService.java:105,145`; `repository/MaquinaRepository.java:26` | Lectura/modificación de contadores sin exclusión mutua. Dos altas o cierres simultáneos pueden perder actualizaciones. | Incorporar bloqueo de fila por máquina y por ticket, orden de adquisición consistente, pruebas reales concurrentes sobre PostgreSQL. |
| CRÍTICO | `db/schema.sql:103,107` | RLS permite incondicionalmente la planta San Joaquín. | Con rol real `silbom_app` y contexto `PLANTA-OTRA` fueron visibles sus 6 máquinas. Exigir contexto de planta por transacción y políticas sin excepción. |
| CRÍTICO | `security/ApiKeyAuthFilter.java:35`; `service/BloqueoService.java:145,176` | Una clave compartida permite actuar sobre tickets de cualquier técnico; el nombre es una declaración del cliente. | No hay identidad ni autorización individual. Requiere decisión de contrato (ver propuesta). |
| CRÍTICO | `service/BloqueoService.java:176`; `db/schema.sql:36` | Borrado definitivo del historial y script de inicialización destructivo. | No volver a ejecutar DROP sobre datos existentes. Proponer historial append-only y CRUD académico sobre recursos administrativos. |
| ALTO | `model/Maquina.java:97,116` | MTBF con cero fallas inventa un valor; cada técnico correctivo suma otra falla aunque intervenga el mismo incidente. | MTBF nulo con cero fallas está solicitado expresamente. Identidad compartida de falla necesita decisión de contrato. |
| ALTO | `src/test/.../SilbomApplicationTests.java:119` | La prueba llamada concurrente ejecuta todas las acciones secuencialmente. | Agregar transacciones y conexiones independientes, barreras de sincronización y verificaciones de filas persistidas. |
| ALTO | `exception/GlobalExceptionHandler.java:169` | Excepciones de integridad, parámetros inválidos y métodos no soportados pueden terminar como 500. | Responder 409, 400 y 405 respectivamente sin filtrar SQL ni datos internos. |
| ALTO | `dto/*`; `security/InputSanitizer.java` | Texto válido puede quedar vacío tras sanitizar; notas sin límite; Double admite valores no finitos en llamadas de dominio. | Validar dominio además de DTO, longitudes y resultados de normalización. |
| MEDIO | `pom.xml:10` | Spring Boot 3.3.5 necesita revisión de soporte y vulnerabilidades. | Consultar metadatos oficiales; comprobar compatibilidad antes de cualquier cambio de línea. No confundir listado de versiones con auditoría CVE. |
| MEDIO | `application.properties:30`; `db/schema.sql` | Hibernate update y SQL manual no versionan evoluciones. | Evaluar Flyway sin baseline automático de esquemas desconocidos ni ejecución sobre producción. |
| MEDIO | `repository/BloqueoLotoRepository.java` | Filtros por planta/máquina/estado sin índices compuestos. | Índices no destructivos acordes con consultas existentes. |
| MEDIO | `security/SecurityConfig.java`; `SecurityHeadersFilter.java` | BCrypt es solo un bean; HSTS se envía sobre HTTP y no habilita TLS; cookies no se usan al ser stateless. | Explicar alcance real y probar controles; configuración TLS corresponde al despliegue. |
| BAJO | `README.md`; `docs/SILBOM_Postman_Collection.json` | Garantías legales/físicas exageradas y demostración dependiente de IDs fijos. | Aclarar que la declaración de candado no demuestra energía cero; Postman con IDs capturados dinámicamente. |

## Propuesta de decisiones que cambian contratos

Las tres decisiones fueron autorizadas expresamente por el usuario antes de aplicarlas,
conforme a la sección 13 de la petición.

1. Identidad: aprovechar Spring Security y BCrypt con HTTP Basic para técnicos y
   supervisores, sin añadir JWT. Incorporar recurso administrativo `tecnicos`
   (CRUD con baja lógica), conservar `maquinas` como segundo recurso CRUD y
   enlazar cada nuevo bloqueo con su técnico. El técnico solo libera sus tickets;
   cualquier excepción supervisora debe quedar expresamente auditada. Las
   escrituras de seguridad dejarían de depender exclusivamente de la API key
   compartida. Los clientes de escritura/Postman necesitan adaptación y TLS en
   despliegues remotos. No incrustar credenciales de integración en el frontend.
2. Historial: impedir DELETE de tickets activos e históricos; guardar eventos
   append-only de creación y liberación. Baja lógica de máquinas/técnicos para
   preservar referencias. Documentar el cambio frente al DELETE actual.
3. Fallas: añadir `referenciaFalla` obligatoria en nuevas intervenciones
   CORRECTIVAS, compartida por todos los técnicos que atiendan la misma falla.
   Un registro único por planta/máquina/referencia evita contarla varias veces.
   Las PREVENTIVAS no cuentan fallas. Conservar los datos históricos sin inferir
   retrospectivamente qué registros representan un mismo incidente.

Mientras se decide, son independientes las correcciones de concurrencia, RLS,
MTBF sin fallas, errores HTTP, validación, pruebas y documentación verificable.

## Límites y repositorio

GitHub estaba vacío. Se preservó una base original en una rama separada, sin
crear ni modificar main. El PR se preparará contra esa base si no aparece una
rama main remota. La lectura Git funciona; el primer acceso a la API GitHub fue
rechazado (403), pendiente de distinguir política de red y acceso API.

La rúbrica suministrada en la petición es la referencia; no se dispone de una
rúbrica oficial adicional. Los resultados finales, cumplimiento por requisito,
matriz de controles y pendientes se actualizarán con evidencia de esta ejecución.

## Resultado de las correcciones

- CRÍTICO: concurrencia resuelta con locks de técnico → máquina → ticket. El
  identificador de máquina se consulta sin cargar previamente una entidad obsoleta
  al liberar. La segunda liberación devuelve 409 y no modifica contadores.
- CRÍTICO: RLS de cinco tablas con contexto transaccional. Se elimina también la
  política original `bloqueos_planta_isolation_policy`, cuyo nombre era distinto
  al patrón de tablas. Se comprobó la actualización de la base original: cero
  máquinas y cero tickets visibles con contexto de otra planta.
- CRÍTICO: identidades individuales, BCrypt y roles efectivos. El dueño viene de
  autenticación; solo él libera. Supervisor administra recursos y consulta notas,
  pero no retira bloqueos ajenos. La antigua API key no autoriza escrituras.
- CRÍTICO: eventos append-only, transición de ticket restringida por trigger y
  privilegios SQL sin DELETE. Baja lógica de máquinas/técnicos; historia conservada.
- ALTO: una referencia de falla por máquina/planta cuenta una sola vez aunque
  varios técnicos participen. MTBF nulo con cero fallas; no división por cero ni
  cambios de horas por tiempo de reloj. No se reinterpreta el historial antiguo.
- ALTO: validaciones tras normalizar texto, límites y valores no finitos;
  parámetros, JSON, métodos, media type e integridad tienen HTTP apropiado.
- MEDIO: Boot actualizado de 3.3.5 a 3.5.16 (misma versión principal), compatible
  con las pruebas Java 21. Se añadieron índices, SQL de evolución y CI reproducible.
- BAJO: README, modelo y Postman se ajustaron a los contratos reales; se retiraron
  garantías legales/físicas y comentarios que contradecían la implementación.

### Pruebas realizadas

Ejecución final `bash scripts/test-postgres.sh`, con Maven y proxy configurados:

| Comprobación | Resultado |
|---|---|
| Dominio, Mockito, criptografía y HTTP/H2 | 37 pruebas; 0 fallos, 0 errores, 0 omitidas |
| Integración PostgreSQL 17 con rol real no propietario | 7 pruebas; 0 fallos, 0 errores, 0 omitidas |
| Package/repackage y failsafe verify | BUILD SUCCESS con Java 21 y Boot 3.5.16 |
| Esquema repetido y eliminación de política RLS antigua | Comprobados en base efímera propia |
| Migración sobre la base de desarrollo original | Respaldo local previo; conserva las 6 máquinas y los 3 tickets originales |
| Colección Postman ejecutada con Newman 6.2.1 | 24 peticiones y 30 assertions; 0 fallos |
| GitHub Actions | Workflow añadido; ejecución remota no verificada desde la API bloqueada |
| Consulta CVE/soporte oficial | No ejecutable: CONNECT del proxy devuelve 403 para OSV/Spring |

Las pruebas PostgreSQL usan conexiones/transacciones distintas, dos identidades
simultáneas, cierre doble, rollback, persistencia, FK, CHECK, cifrado almacenado,
permisos SQL y contexto RLS. No se deshabilitan filtros ni aserciones para pasar.
Los contadores del filtro en tests usan IPs distintas por caso; un caso específico
agota la cuota real y exige 429/Retry-After.

Las credenciales de la ejecución y el reporte Newman con valores se mantienen
fuera del repositorio; los temporales sensibles de Newman se eliminan. Los
resultados detallados actuales están en target/surefire-reports y
 target/failsafe-reports. El log y respaldo local están bajo
/workspace/.silbom-cloud, sin valores en este documento.

### Cumplimiento de los requisitos suministrados del Hito 2

| # | Requisito | Evaluación y evidencia |
|---|---|---|
| 1 | Dos recursos con CRUD | Máquinas y técnicos; POST/GET/PUT/DELETE lógico comprobados por HTTP y Postman. Tickets no se eliminan. |
| 2 | API REST | Rutas/verbos/contratos y cambios compatibles identificados en README/Postman. |
| 3 | PostgreSQL real | Siete pruebas de integración y aplicación arrancada contra PostgreSQL 17. |
| 4 | Multicapa | Controladores delegan, DTOs de entrada/salida, reglas/transacciones en servicios, consultas en repositorios. |
| 5 | HTTP correcto | Tests 200/201/204/400/401/403/404/405/409/415/429. |
| 6 | Validación | Jakarta, normalización, dominio, FK y CHECK; pruebas de rechazo. |
| 7 | Credenciales externas | .env ignorado; bootstrap sin clave predeterminada; valores de test son fixtures, no credenciales de despliegue. |
| 8 | SQL reproducible | Schema y migración transaccionales/repetibles sin DROP TABLE; Flyway evaluado y diferido sin baseline automático. |
| 9 | Documentación técnica | README, modelo, auditoría y matriz de controles actualizados. |
| 10 | Postman | Colección ejecutada: 24 peticiones, 30 comprobaciones exitosas. |
| 11 | Otro computador | Pasos, roles PostgreSQL y variables documentados; no se afirma haber probado otra máquina física o cada SO. |
| 12 | Pruebas automatizadas | 44 pruebas Maven y workflow CI; resultado remoto CI aún no consultado. |

No se dispone de una rúbrica oficial diferente de la lista entregada por el
usuario; esta evaluación no es una calificación académica garantizada.

### Pendientes y Hito 3

1. Crear el PR: Git funciona y la base original está publicada; la API GitHub
   devuelve 403 del proxy. Se guardó el dominio api.github.com en el borrador de
   red; guardar/persistir el borrador no habilita por sí solo el acceso en runtime.
2. Completar auditoría CVE y confirmar soporte vigente desde fuentes oficiales
   cuando estén accesibles. Actualizar versiones no equivale a demostrar ausencia
   de vulnerabilidades. No se migró a Boot 4 sin una evaluación específica.
3. Frontend sigue desconectado y NFC/QR es simulado. La integración debe usar el
   nuevo contrato y revisar CSRF para uso de Basic desde navegadores (stateless
   no elimina ese riesgo). Evitar credenciales compartidas en JavaScript.
4. Modelar explícitamente el período de medición MTBF, instrumentación de horas y
   procedimientos para corregir datos, sin inventar métricas ni fallas pasadas.
5. Recuperación/rotación de cuentas/claves, paginación, observabilidad y eventos
   administrativos. Rate limiting actual es por IP/proceso, no distribuido.
6. Definir procedimiento para tickets legados activos sin responsable autenticado;
   se conservan y no se proporciona una liberación privilegiada improvisada.

La base de desarrollo recibió una migración respaldada; no se modificó producción.
No se fusiona el trabajo automáticamente. El PR usará baseline/silbom-original
como base porque main no existía y la instrucción prohíbe modificarla directamente.

## Archivos modificados y añadidos

- `.env.example`
- `.github/workflows/backend.yml`
- `README.md`
- `db/migrations/V2__integridad_loto.sql`
- `db/modelo_datos.md`
- `db/schema.sql`
- `docs/SILBOM_Postman_Collection.json`
- `docs/auditoria-backend.md`
- `docs/controles-seguridad.md`
- `pom.xml`
- `scripts/test-postgres.sh`
- `src/main/java/cl/silbom/backend/config/DataInitializer.java`
- `src/main/java/cl/silbom/backend/config/PlantaTransactionAspect.java`
- `src/main/java/cl/silbom/backend/controller/BloqueoController.java`
- `src/main/java/cl/silbom/backend/controller/TecnicoController.java`
- `src/main/java/cl/silbom/backend/dto/BloqueoCreateDTO.java`
- `src/main/java/cl/silbom/backend/dto/BloqueoResponseDTO.java`
- `src/main/java/cl/silbom/backend/dto/EventoLotoResponseDTO.java`
- `src/main/java/cl/silbom/backend/dto/MaquinaCreateDTO.java`
- `src/main/java/cl/silbom/backend/dto/MaquinaUpdateDTO.java`
- `src/main/java/cl/silbom/backend/dto/TecnicoCreateDTO.java`
- `src/main/java/cl/silbom/backend/dto/TecnicoResponseDTO.java`
- `src/main/java/cl/silbom/backend/dto/TecnicoUpdateDTO.java`
- `src/main/java/cl/silbom/backend/exception/GlobalExceptionHandler.java`
- `src/main/java/cl/silbom/backend/model/BloqueoLoto.java`
- `src/main/java/cl/silbom/backend/model/EventoLoto.java`
- `src/main/java/cl/silbom/backend/model/Falla.java`
- `src/main/java/cl/silbom/backend/model/Maquina.java`
- `src/main/java/cl/silbom/backend/model/Rol.java`
- `src/main/java/cl/silbom/backend/model/Tecnico.java`
- `src/main/java/cl/silbom/backend/repository/BloqueoLotoRepository.java`
- `src/main/java/cl/silbom/backend/repository/EventoLotoRepository.java`
- `src/main/java/cl/silbom/backend/repository/FallaRepository.java`
- `src/main/java/cl/silbom/backend/repository/MaquinaRepository.java`
- `src/main/java/cl/silbom/backend/repository/TecnicoRepository.java`
- `src/main/java/cl/silbom/backend/security/ActorActual.java`
- `src/main/java/cl/silbom/backend/security/ApiKeyAuthFilter.java`
- `src/main/java/cl/silbom/backend/security/InputSanitizer.java`
- `src/main/java/cl/silbom/backend/security/SecurityConfig.java`
- `src/main/java/cl/silbom/backend/security/SecurityFilterRegistration.java`
- `src/main/java/cl/silbom/backend/security/SecurityHeadersFilter.java`
- `src/main/java/cl/silbom/backend/security/TecnicoPrincipal.java`
- `src/main/java/cl/silbom/backend/security/TecnicoUserDetailsService.java`
- `src/main/java/cl/silbom/backend/service/BloqueoService.java`
- `src/main/java/cl/silbom/backend/service/MaquinaService.java`
- `src/main/java/cl/silbom/backend/service/TecnicoService.java`
- `src/main/resources/application.properties`
- `src/test/java/cl/silbom/backend/PostgresIT.java`
- `src/test/java/cl/silbom/backend/SilbomApplicationTests.java`
- `src/test/java/cl/silbom/backend/model/MaquinaTests.java`
- `src/test/java/cl/silbom/backend/security/CryptoUtilsTests.java`
- `src/test/java/cl/silbom/backend/security/TecnicoServiceTests.java`
- `src/test/resources/application-postgres-test.properties`
- `src/test/resources/application-test.properties`
