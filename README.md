# SILBOM — Sistema LOTO (Hito 3: Producto Funcional e Integración Full-Stack)

API REST universitaria y clientes frontend integrados para gestión de maquinaria e intervenciones de seguridad LOTO (*Lockout / Tagout*). Proyecto desarrollado con Java 21, Spring Boot 3.5.16, Maven, JPA/Hibernate, Spring Security y PostgreSQL 15–17 (probado en PostgreSQL 17).

> **Aviso de seguridad industrial:** El estado `bloqueada` en SILBOM constituye un **registro digital de control y trazabilidad**. SILBOM no coloca candados físicos, no desenergiza actuadores ni certifica automáticamente energía cero; la confirmación del bloqueo es una declaración de responsabilidad del técnico autenticado bajo la normativa chilena DS 594.

---

## Arquitectura del Sistema

El proyecto implementa una arquitectura monolítica modular en capas bajo el paquete base `cl.silbom.backend`, desacoplada en dos clientes de interfaz de usuario:

```
[ Cliente Web Supervisor (PC) ]      [ Cliente Móvil Técnico (Android) ]
     http://localhost:8080/             http://localhost:8080/app/  o  APK nativo
               │                                      │
               └───────────────┬──────────────────────┘
                               │  HTTP Basic + REST (/api/v1)
                               ▼
               ┌───────────────────────────────┐
               │    Controller (REST / DTO)    │
               ├───────────────────────────────┤
               │   Service (Reglas y Bloqueo)  │
               ├───────────────────────────────┤
               │ Repository (JPA / Row Locks)  │
               ├───────────────────────────────┤
               │    PostgreSQL 17 (RLS / DB)   │
               └───────────────────────────────┘
```

- **Consola Web de Supervisión (PC):** Servida en `/` (o `/index.html`), diseñada exclusivamente para monitoreo centralizado de planta, visualización de KPIs de disponibilidad, cálculo de MTBF, auditoría de eventos y administración de usuarios técnicos.
- **Cliente Móvil de Terreno (Android):** Disponible como interfaz web responsiva en `/app/` y distribuido como instalador nativo Android (`.apk`). Optimizado para operación con una sola mano, lectura de etiquetas NFC, escaneo de respaldo QR y confirmación de candado físico.
- **Backend Monolítico:** `Controller` valida DTOs y gestiona códigos HTTP estándar; `Service` ejecuta la lógica de negocio, control de concurrencia pesimista (`Tecnico` → `Maquina` → `BloqueoLoto`) y cifrado AES-256-GCM de notas; `Repository` aísla las consultas SQL parametrizadas; `PlantaTransactionAspect` aplica seguridad RLS por planta a nivel de sesión de base de datos.

Tablas relacionales: `maquinas`, `tecnicos`, `fallas`, `bloqueos_loto` y `eventos_loto`. Todas las referencias compuestas garantizan aislamiento multi-inquilino (*multi-tenant*) por planta.

---

## Instalación de App Móvil (Android)

La aplicación móvil de terreno `SILBOM Técnico` está enfocada de forma **exclusiva en el sistema operativo Android** (versión mínima 7.0 / SDK 24, compatible hasta Android 14 / SDK 34).

> **Distribución oficial de la aplicación:**
> El instalador ejecutable `.apk` se distribuye exclusivamente a través de la pestaña de Releases del repositorio:
> 👉 **[Descargar SILBOM Técnico en GitHub Releases](https://github.com/Bvstivn-A/Silbom_Pruebas/releases)**
>
> Actualmente está disponible una **Pre-release de pruebas** con el binario compilado y firmado (`silbom-tecnico.apk`). De acuerdo con el **Anexo B (Reglas del repositorio)** de la asignatura, los archivos binarios compilados no se almacenan en las ramas de código fuente de Git para evitar el engrosamiento del historial y prevenir conflictos de fusión.

### Justificación de enfoque exclusivo Android (Sin soporte iOS)
Se descartó de manera formal el soporte para terminales iOS (iPhone / iPad) por las siguientes razones de arquitectura y entorno:
1. **Entorno de compilación:** Empaquetar aplicaciones para iOS requiere hardware macOS y Xcode, inexistente en las estaciones de trabajo del equipo.
2. **Costo de licenciamiento:** La publicación exige suscripción anual de \$99 USD al *Apple Developer Program*.
3. **Restricción de sideloading:** iOS restringe la instalación libre de ejecutables fuera de la App Store o TestFlight.
4. **Realidad de planta industrial:** En plantas productivas chilenas predominan ampliamente los terminales rugerizados y smartphones corporativos Android con chips NFC integrados.

### Instalación y conexión en teléfono Android
1. Descarga el archivo `silbom-tecnico.apk` desde [GitHub Releases](https://github.com/Bvstivn-A/Silbom_Pruebas/releases).
2. Abre el archivo en tu teléfono Android y permite la instalación de aplicaciones de orígenes desconocidos.
3. Abre la aplicación y pulsa el botón **⚙️ IP** en la barra superior para indicar la dirección IP de tu servidor backend (por ejemplo: `http://192.168.1.100:8080`).
4. Si estás en una red Wi-Fi institucional con aislamiento de clientes (AP Isolation), puedes conectar el teléfono por cable USB con depuración activada y ejecutar en tu PC:
   ```bash
   adb reverse tcp:8080 tcp:8080
   ```
   Luego en la app selecciona el botón **💻 Localhost (8080)** para conectar directamente por USB.

---

## Instalación y Despliegue en localhost

### 1. Requisitos previos
- **Java Development Kit (JDK):** Versión 21 instalada (`javac -version`).
- **Apache Maven:** Versión 3.9 o superior (`mvn -version`).
- **PostgreSQL:** Versión 15, 16 o 17 en ejecución (`psql --version`).

### 2. Configurar la base de datos
Inicia el servicio PostgreSQL y crea los roles de migración y aplicación (con contraseñas independientes y privilegios mínimos):

```bash
sudo -u postgres createuser --pwprompt silbom_owner
sudo -u postgres createuser --pwprompt silbom_app
sudo -u postgres createdb --owner=silbom_owner silbom_db
```

Ejecuta los scripts transaccionales en orden con el usuario propietario:

```bash
psql -h localhost -U silbom_owner -d silbom_db -v ON_ERROR_STOP=1 -f db/schema.sql
psql -h localhost -U silbom_owner -d silbom_db -v ON_ERROR_STOP=1 -f db/migrations/V2__integridad_loto.sql
```

### 3. Configurar variables de entorno (.env)
Copia la plantilla de ejemplo y genera una clave de cifrado criptográfica de 32 bytes (256 bits) en Base64:

```bash
cp .env.example .env
chmod 600 .env
openssl rand -base64 32
```

Edita `.env` con los valores de conexión de tu máquina local:

```ini
DB_URL=jdbc:postgresql://localhost:5432/silbom_db
DB_USERNAME=silbom_app
DB_PASSWORD=tu_contraseña_silbom_app
ENCRYPTION_SECRET=pega_aqui_la_clave_base64_generada
PLANTA_ID=PLANTA-SAN-JOAQUIN
SERVER_PORT=8080
SILBOM_BOOTSTRAP_USERNAME=supervisor-local
SILBOM_BOOTSTRAP_PASSWORD=SilbomSup_7674a15c5e37d628
```

### 4. Compilar y arrancar la aplicación
Desde la raíz del repositorio, ejecuta:

```bash
mvn spring-boot:run
```

O genera el paquete JAR ejecutable:

```bash
mvn clean package
java -jar target/silbom-backend-1.0.0.jar
```

El servidor iniciará en el puerto `8080`. En el primer arranque, `DataInitializer` creará de manera automática la cuenta supervisora inicial y los 6 equipos maestros de demostración para la Planta San Joaquín.

---

## Recorrido de Prueba en localhost (Evaluación Hito 3)

Para evaluar el sistema completo punta a punta en un computador de revisión sin requerir hardware móvil ni tarjetas físicas NFC, sigue este flujo guiado:

### Paso 1: Acceso a la Consola de Supervisión (PC)
1. Abre tu navegador en **`http://localhost:8080/`**.
2. Observa la insignia de estado superior: debe indicar **`🟢 API Conectada (PostgreSQL)`**.
3. En la barra de autenticación, inicia sesión con las credenciales de supervisor:
   - **Usuario:** `supervisor-local`
   - **Contraseña:** `SilbomSup_7674a15c5e37d628` *(o la definida en tu `.env`)*
4. En el panel podrás ver la grilla de las 6 máquinas de la planta (`EQ-ENV-01`, `EQ-PR-04`, `EQ-EXT-02`, `EQ-MEZ-01`, `EQ-COM-03`, `EQ-TR-01`), el cálculo dinámico de horas operativas y MTBF, y el historial de tickets.
5. Haz clic en **👥 Técnicos** y registra un nuevo técnico para la prueba:
   - **Username:** `tecnico1`
   - **Nombre:** `Técnico de Terreno 1`
   - **Contraseña:** `SilbomTec_123456` *(mínimo 12 caracteres)*
   - **Rol:** `Técnico LOTO`

### Paso 2: Simulación de Escaneo NFC en Terreno (Cliente Técnico)
1. Abre una nueva pestaña o ventana en **`http://localhost:8080/app/?maquina=EQ-ENV-01`**.
   > **Nota de ingeniería:** El parámetro `?maquina=EQ-ENV-01` simula la lectura automática de una etiqueta NFC física en terreno (mismo comportamiento que se produce al acercar el teléfono Android a la máquina).
2. En la barra inferior, selecciona la pestaña **👤 Técnicos / Sesión** e ingresa con:
   - **Usuario:** `tecnico1`
   - **Contraseña:** `SilbomTec_123456`
3. Regresa a la pestaña **🔒 Operar LOTO**:
   - Observa que el **Paso 1 (Identificar)** detectó automáticamente la máquina `EQ-ENV-01 · Envasadora Rotativa Automática` vía NFC simulado.
   - En el **Paso 2 (Bloqueo)**, selecciona Tipo de Intervención: `CORRECTIVA`.
   - Ingresa el Motivo: `Falla en cabezal de sellado térmico`.
   - Ingresa una Nota Confidencial: `Se desenergiza disyuntor Q3 y se purga válvula neumática de mordazas`. *(Esta nota se almacenará cifrada con AES-256-GCM)*.
   - Marca la casilla obligatoria: **`Confirmo instalación de candado físico (DS 594)`**.
   - Haz clic en **Aplicar Bloqueo LOTO**.
4. La interfaz avanzará al **Paso 3 (En Curso)** mostrando el ticket generado con estado `ACTIVO`.

### Paso 3: Verificación de Persistencia tras Recarga (F5)
1. Vuelve a la pestaña de la Consola del Supervisor en **`http://localhost:8080/`**.
2. Presiona **`F5`** (o recargar página).
3. **Resultado observable:** La máquina `EQ-ENV-01` permanece en color rojo con el estado **`BLOQUEADA (1 bloqueo activo)`**. Esto demuestra que el estado no depende de variables volátiles del navegador ni de `localStorage`, sino que persiste íntegramente en la base de datos PostgreSQL.

### Paso 4: Prueba de Control de Acceso y Propiedad del Candado (`403 Forbidden`)
1. En la consola del supervisor (`http://localhost:8080/`), intenta hacer clic en el botón de desbloqueo de `EQ-ENV-01`.
2. El sistema rechazará la acción y el backend devolverá **`403 Forbidden`**.
   > **Regla inquebrantable de seguridad:** Ningún supervisor ni otro técnico puede vulnerar la propiedad del candado digital de un compañero. Solo el usuario que aplicó el bloqueo tiene la potestad de liberarlo.

### Paso 5: Desbloqueo y Trazabilidad de Auditoría
1. En la pestaña del técnico (`http://localhost:8080/app/`), logueado como `tecnico1`, pulsa **Liberar Bloqueo LOTO**.
2. Confirma la liberación. La máquina pasa a estado desbloqueado.
3. En la Consola del Supervisor (`http://localhost:8080/`), recarga (`F5`): `EQ-ENV-01` vuelve al estado **OPERATIVA** (verde).
4. En el historial de tickets de la consola, haz clic en **Auditoría / Eventos**: podrás verificar la línea de tiempo inmutable (`CREACION` → `LIBERACION`) con marcas de tiempo exactas y el texto de la nota descifrado exitosamente.

---

## Contrato de API REST (`/api/v1`)

| Recurso | Método y Ruta | Permiso requerido | Descripción |
|---|---|---|---|
| **Máquinas** | `GET /maquinas` | Público | Listado de máquinas y estado de bloqueos |
| | `GET /maquinas/{id}` | Público | Detalle de un equipo específico |
| | `POST /maquinas` | Supervisor | Alta de nuevo equipo en la planta |
| | `PUT /maquinas/{id}` | Supervisor | Actualización de datos operativos |
| | `DELETE /maquinas/{id}` | Supervisor | Baja lógica (204 No Content; no borra historial) |
| **Técnicos** | `GET /tecnicos` | Supervisor | Listado de técnicos activos |
| | `POST /tecnicos` | Supervisor | Registro de nuevo técnico con contraseña BCrypt |
| | `PUT /tecnicos/{id}` | Supervisor | Actualización de perfil o rol |
| | `DELETE /tecnicos/{id}` | Supervisor | Baja lógica de técnico (conserva referencias) |
| **Bloqueos LOTO** | `GET /bloqueos` | Autenticado | Consulta de tickets (filtrable por máquina y estado) |
| | `GET /bloqueos/{id}` | Autenticado | Detalle de ticket y nota confidencial si es dueño |
| | `POST /bloqueos` | Autenticado | Creación de ticket con bloqueo pesimista de fila |
| | `PUT /bloqueos/{id}/liberar` | Dueño del ticket | Liberación obligatoria por el técnico responsable |
| | `DELETE /bloqueos/{id}` | Prohibido | Retorna siempre `409 Conflict` (historial inmutable) |
| **Auditoría** | `GET /bloqueos/{id}/eventos` | Autenticado | Línea de tiempo de eventos del ticket |

### Códigos de respuesta HTTP
- **`200 OK`**: Consulta, actualización o liberación exitosa.
- **`201 Created`**: Creación exitosa de máquina, técnico o bloqueo LOTO.
- **`204 No Content`**: Baja lógica procesada correctamente.
- **`400 Bad Request`**: Entrada inválida (contraseña < 12 caracteres, campos faltantes).
- **`401 Unauthorized`**: Credenciales ausentes o incorrectas.
- **`403 Forbidden`**: Intento de liberar un candado ajeno o acceder a rutas de supervisor sin el rol.
- **`404 Not Found`**: Recurso inexistente o dado de baja lógica.
- **`409 Conflict`**: Máquina ya bloqueada con candado exclusivo o intento de eliminar historial.
- **`429 Too Many Requests`**: Límite de tasa excedido por IP (filtro RateLimit defensivo).

---

## Verificación Automatizada y Tests

Para ejecutar la suite completa de pruebas unitarias y de integración:

```bash
mvn -B -ntp clean test
```

La suite ejecuta **42 pruebas automatizadas** sin fallos:
- Pruebas de dominio y cálculo de MTBF (`MaquinaTests`).
- Pruebas de controladores, aspect RLS, transacciones concurrentes, control de acceso IDOR y filtros de seguridad HTTP (`SilbomApplicationTests`).
