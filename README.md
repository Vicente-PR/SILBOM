# SILBOM — Backend Hito 2

API REST universitaria para maquinaria e intervenciones LOTO. Java 21, Spring
Boot 3.5.16, Maven, JPA, Spring Security y PostgreSQL 15–17 (probado con 17).

El estado `bloqueada` es un **registro digital**. SILBOM no instala candados,
no aísla energía, no verifica energía cero ni certifica cumplimiento legal.
La confirmación del candado es una declaración del técnico autenticado.

## Arquitectura

Monolito en `cl.silbom.backend`: controller recibe HTTP y valida DTO; service
aplica autorización, reglas y transacciones; repository persiste con consultas
parametrizadas y bloqueos de filas; model controla transiciones; DTO evita
exponer entidades/hashes; exception normaliza errores; security autentica,
autoriza y cifra; config fija el contexto RLS y carga datos iniciales.

Tablas: maquinas, tecnicos, fallas, bloqueos_loto y eventos_loto. Los vínculos
incluyen la planta mediante claves foráneas compuestas. El ID de máquina es
único en todo el sistema.

## Instalación en otro computador

1. Instala JDK 21, Maven 3.9+ y PostgreSQL 15, 16 o 17 con los instaladores
   oficiales o el gestor de paquetes de tu sistema. Comprueba `java -version`,
   `javac -version`, `mvn -version` y `psql --version`.
2. Inicia PostgreSQL. Crea un propietario/migrador y un usuario de aplicación
   separados; ninguno necesita superusuario. Ejemplo Linux:

   ```bash
   sudo -u postgres createuser --pwprompt silbom_owner
   sudo -u postgres createuser --pwprompt silbom_app
   sudo -u postgres createdb --owner=silbom_owner silbom_db
   ```

3. Desde la raíz, ejecuta como propietario/migrador:

   ```bash
   psql -h localhost -U silbom_owner -d silbom_db -v ON_ERROR_STOP=1 -f db/schema.sql
   ```

   El script y `db/migrations/V2__integridad_loto.sql` son transaccionales y
   repetibles, sin DROP TABLE. Conceden a silbom_app permisos sin DDL ni DELETE
   físico. Otro rol se selecciona con `-v app_role=nombre_del_rol`.
   La API no debe ser propietaria ni usar superusuario o BYPASSRLS.
   Para una base original existente, respáldala y ejecuta como su propietario.
   La migración conserva tickets/notas y no inventa identidades ni agrupa fallas
   antiguas. Si una restricción falla, se revierte la transacción: no se corrigen
   registros a ciegas. No uses producción para demostraciones o pruebas.

4. Prepara la configuración:

   ```bash
   cp .env.example .env
   chmod 600 .env
   openssl rand -base64 32
   ```

   Edita .env con la contraseña real del rol, una clave Base64 de exactamente
   32 bytes y credenciales iniciales individuales. Conserva la clave AES para
   poder leer notas anteriores. No publiques .env. Spring lo carga como archivo
   **Java properties** desde el directorio de ejecución, sin `source`; no uses
   comillas de shell. Las variables exportadas tienen prioridad.

5. Compila, prueba y arranca desde la raíz:

   ```bash
   mvn clean package
   java -jar target/silbom-backend-1.0.0.jar
   ```

   Alternativa: `mvn spring-boot:run`. Puerto predeterminado 8080. Si la planta
   no tiene máquinas se cargan seis equipos y tres tickets históricos de demo.
   Las variables de bootstrap crean la primera cuenta supervisora sin contraseña
   predeterminada; retíralas después. En la nube de esta sesión usa activate.sh
   y los ajustes Maven/proxy guardados bajo /workspace/.silbom-cloud.

## Variables

| Variable | Propósito |
|---|---|
| DB_URL | JDBC PostgreSQL, por defecto base local silbom_db |
| DB_USERNAME / DB_PASSWORD | Rol de aplicación, nunca el migrador |
| ENCRYPTION_SECRET | Base64 de 32 bytes para AES-256-GCM |
| PLANTA_ID | Planta definida por el servidor, no por el cliente |
| SERVER_PORT | Puerto, por defecto 8080 |
| SILBOM_BOOTSTRAP_USERNAME | Supervisor inicial, solo primera instalación |
| SILBOM_BOOTSTRAP_PASSWORD | Mínimo 12 caracteres, máximo 72 bytes UTF-8 |

## Identidad, permisos y compatibilidad

HTTP Basic individual y BCrypt costo 12 reemplazan a la API key compartida.
APP_API_KEY deja de autorizar operaciones. Fuera de loopback se necesita TLS
configurado en un servidor/proxy: una cabecera HSTS no instala HTTPS.

| Operación | Permiso |
|---|---|
| Lectura de máquinas | Pública para el panel |
| CRUD de máquinas/técnicos | Supervisor de la planta |
| Lectura de tickets/eventos | Técnico o supervisor de la planta |
| Notas confidenciales | Responsable del ticket o supervisor |
| Crear bloqueo | Técnico/supervisor; dueño obtenido de autenticación |
| Liberar bloqueo | Solo su responsable, incluso frente al supervisor |
| DELETE de tickets | Siempre 409: no se elimina historial |

DELETE en máquinas/técnicos es baja lógica (204); el GET posterior devuelve 404.
Se conservan referencias e identidades y no se reutilizan usuarios dados de baja.
No se dan de baja equipos/técnicos con bloqueos activos ni se retira el propio
rol supervisor. Los tickets legados sin identidad necesitan un procedimiento
supervisado futuro; no se permite eludir la propiedad del candado.

Cambios de contrato aprobados: Basic individual, GET de tickets autenticado,
referenciaFalla obligatoria para CORRECTIVA, MTBF nullable y ningún DELETE de
historial. nombreTecnico es un campo legado opcional ignorado para identificar
al responsable. El cliente debe mostrar MTBF null como «no calculable».
El HTML docs/Silbom1.html sigue siendo un prototipo offline, sin llamadas API.

## API

Base `/api/v1`; User-Agent obligatorio. Configura Basic Auth en Postman con
credenciales locales, sin guardarlas en colecciones compartidas ni incrustar
credenciales de integración en el navegador.

| Recurso | Rutas |
|---|---|
| Máquinas | GET/POST /maquinas; GET/PUT/DELETE /maquinas/{id} |
| Técnicos | GET/POST /tecnicos; GET/PUT/DELETE /tecnicos/{id} |
| Tickets | GET/POST /bloqueos; GET/DELETE /bloqueos/{id} |
| Liberación | PUT /bloqueos/{id}/liberar |
| Eventos | GET /bloqueos/{id}/eventos |

Filtros: /maquinas?bloqueada=true y
/bloqueos?maquinaId=EQ-ENV-01&estado=ACTIVO.

Crear máquina como supervisor:

```json
{"id":"EQ-DEMO-01","nombre":"Equipo demo","area":"Taller","icono":"X","horasOperativas":100.0,"fallasCorrectivas":0}
```

Crear técnico como supervisor (contraseña ilustrativa a reemplazar localmente):

```json
{"username":"tecnico.demo","nombre":"Técnico demo","password":"CAMBIAR_CONTRASENA_LOCAL","rol":"TECNICO"}
```

Registrar bloqueo con credencial individual:

```json
{"maquinaId":"EQ-DEMO-01","tipoIntervencion":"CORRECTIVA","motivo":"Inspección de falla registrada","confirmacionCandadoDs594":true,"referenciaFalla":"FALLA-2026-001"}
```

Todos los participantes de una misma falla usan la misma referencia en esa
máquina/planta; solo su primera aparición suma una falla. Una avería nueva usa
otra referencia. PREVENTIVA omite referenciaFalla y no incrementa fallas.

MTBF = horas operativas declaradas / fallas observadas, redondeado a un decimal;
sin fallas es null. No avanza con el reloj ni predice exactamente futuras fallas.
El supervisor debe mantener horas y conteos del mismo período de medición; aún
no se modelan explícitamente sus fechas.

HTTP: 200 lectura/actualización/liberación; 201 alta; 204 baja lógica; 400 entrada
inválida; 401 credencial ausente/inválida; 403 sin permiso; 404 inexistente; 405
verbo no admitido; 409 conflicto de estado/integridad/concurrencia; 429 límite.
No se devuelve SQL ni stack trace.

## Pruebas y demostración

```bash
mvn test  # dominio, Mockito, seguridad y HTTP con H2
```

Para PostgreSQL real se requiere un administrador de pruebas con CREATEDB y
un rol de aplicación no propietario ya existente. Configura PGHOST, PGPORT,
PGUSER y, si corresponde, PGPASSWORD de forma segura. Exporta TEST_DB_USERNAME y
TEST_DB_PASSWORD para el rol de aplicación:

```bash
bash scripts/test-postgres.sh
```

El auxiliar crea una base efímera propia, aplica dos veces el esquema, ejecuta
`mvn -Ppostgres-it verify` y elimina solo esa base al terminar. No recibe un
nombre de base de producción. La suite verifica transacciones/conexiones
independientes, doble liberación, rollback, persistencia, CHECK/FK, RLS y
protección de historial. `mvn test` por sí solo no ejecuta esa suite. Resultados:
`target/surefire-reports` y `target/failsafe-reports` de la ejecución actual.

Importa docs/SILBOM_Postman_Collection.json y configura sus variables vacías en
un entorno local. Los IDs se capturan dinámicamente; demuestra CRUD, permisos,
Multi-LOTO y errores sin borrar tickets. La máquina termina de baja lógica y su
historial permanece.

## Seguridad y pendientes

Ver docs/controles-seguridad.md y docs/auditoria-backend.md para evidencia,
limitaciones y evaluación de la rúbrica. Boot pasó de 3.3.5 a 3.5.16 desde Maven
Central, conservando Java 21 y la línea principal 3.x. La consulta externa de
CVE/soporte puede estar bloqueada: no se afirma ausencia de vulnerabilidades.

Hito 3: frontend conectado, QR/NFC real, período de medición explícito,
recuperación/rotación de credenciales, paginación, observabilidad y procedimiento
para tickets legados sin identidad. No se implementa una liberación privilegiada
que evite el permiso del responsable.

Se evaluó Flyway: es conveniente versionar migraciones, pero no se activa un
baseline automático sobre bases existentes desconocidas. En este alcance se
usa SQL transaccional/repetible; Flyway requiere un inventario y baseline
explícito, probado y respaldado.

La revisión posterior de higiene, normalización de notas y documentación pedagógica
está registrada en [Auditoría de la propuesta Gemini](docs/REGISTRO_AUDITORIA_GEMINI.md).
