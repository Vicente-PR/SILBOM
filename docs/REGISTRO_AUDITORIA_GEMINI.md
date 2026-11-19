---
registro_version: 1
proyecto: SILBOM
fecha: 2026-11-05
rama: improvement/backend-hito2
base_commit: 6e38506ca3c118d0b7c020cb5e0268ec00fbd8b0
fuente: instrucciones_de_ejecuci_n_para_agente_ia.md
fuente_sha256: 2405c577f964c81743ca0a55627b26bc55f2746ae99da668e88ca25217d71303
alcance: higiene, normalizacion_de_notas, documentacion_pedagogica
---

# Auditoría de la propuesta Gemini y registro de implementación

Este documento describe cambios de esta revisión, no una reescritura del backend.
La propuesta adjunta se contrastó con la implementación existente antes de aplicar
sus recomendaciones. Los informes anteriores describen la evolución previa.

## Resultado de la auditoría de las recomendaciones

| Propuesta | Diagnóstico | Decisión |
|---|---|---|
| Normalizar notas antes de cifrar | El alta cifraba y respondía directamente con el texto original | Aplicar la normalización existente y devolver el mismo texto que se cifra |
| Eliminar API key de tests | La propiedad de la autenticación compartida seguía presente y ya no se usaba | Eliminar solo esa línea |
| Formato legible y Javadoc | Había instrucciones compactas y faltaban explicaciones de responsabilidades | Documentar y formatear las 24 clases indicadas |
| Mantener rutas, DTO y firmas | Son contratos utilizados por clientes y pruebas | Conservar nombres, componentes, anotaciones de validación y firmas |
| Mantener locks, RLS y SQL | Son garantías de la auditoría anterior, no objeto de esta modificación | Conservar lógica de locks y aspecto; SQL sin cambios |
| Exigir exactamente 37 pruebas | Las 37 pruebas originales deben seguir pasando | Conservarlas y sumar cinco casos de regresión; el nuevo total esperado es 42 en memoria |

La revisión también corrigió precisiones de la propuesta en los comentarios:

- La propiedad del ticket es una regla de autorización del sistema. El código
  no prueba cumplimiento del DS 594 ni verifica quién retira el candado físico.
- `updatable=false`, ausencia de setters y una interfaz sin `delete` reducen
  operaciones disponibles en Java; la protección SQL depende además de triggers
  y permisos de PostgreSQL.
- El límite de BCrypt se explica como 72 **bytes UTF-8**, no 72 caracteres.
  Rechazar entradas superiores evita truncamientos o diferencias entre versiones
  de la biblioteca.
- El aspecto configura métodos transaccionales interceptados por el proxy Spring;
  no se presenta como una interceptación de toda llamada Java o autoinvocación.
- La normalización mediante regex no es un sanitizador HTML general ni sustituye
  el escape contextual en el frontend.

## Cambio funcional: notas confidenciales

Símbolo: `BloqueoService.crearBloqueo(BloqueoCreateDTO)`.

Secuencia posterior al cambio:

1. Conservar las validaciones de entrada, identidad, planta, candado y longitud.
2. Inicializar `notasSanitizadas` a `null`.
3. Si existe texto no blanco, aplicar `InputSanitizer.sanitize`.
4. Si el resultado queda vacío, normalizarlo a `null`.
5. Cifrar con AES-256-GCM únicamente el resultado no nulo.
6. Persistir el ticket y su evento dentro de la transacción existente.
7. Construir la respuesta con `notasSanitizadas`, nunca con el texto original.

| Entrada de notas | Valor lógico guardado y devuelto |
|---|---|
| Texto normal | Texto normalizado por el sanitizador existente |
| Etiquetas y caracteres de control | Texto resultante después de la normalización |
| `null`, cadena vacía o espacios | `null` |
| Contenido eliminado por completo al normalizar | `null` |

La decisión de usar `null` también para contenido eliminado evita que el POST
responda una cadena vacía mientras el GET posterior devuelve `null`. El JSON
mantiene el campo `notasAuditoria` y los mismos códigos HTTP. Los valores de notas
con contenido marcado sí cambian intencionalmente: ahora se devuelve el texto
normalizado. No se transforma ni vuelve a cifrar el historial anterior.

## Cambio de configuración

En `src/test/resources/application-test.properties` se eliminó únicamente:

```properties
silbom.security.api-key=test-api-key-12345
```

Ese valor era una fixture pública de una autenticación ya retirada. No se cambió
ninguna otra propiedad del perfil. No se añadieron credenciales ni valores por
defecto en la configuración de producción.

## Registro dentro del código

Se añadieron o actualizaron 77 cabeceras Javadoc de clases y métodos críticos,
con las secciones **QUÉ HACE** y **POR QUÉ ESTÁ ESCRITO ASÍ**. También se separaron
sentencias y anotaciones y se agregaron llaves en condicionales de una sentencia.
Los getters sencillos conservan nombres descriptivos; las cabeceras de cada
entidad explican su papel y sus límites.

| Archivo en `src/main/java/cl/silbom/backend/` | Responsabilidad documentada |
|---|---|
| `config/PlantaTransactionAspect.java` | Contexto RLS parametrizado en la misma transacción; orden 0/1 y limpieza del pool |
| `config/DataInitializer.java` | Bootstrap sin contraseña por defecto, límite BCrypt y datos LEGADO_IMPORTADO |
| `security/ActorActual.java` | Identidad, rol supervisor y autorización estricta por dueño del ticket |
| `security/TecnicoPrincipal.java` | Adaptación a UserDetails y representación sin hash en toString |
| `security/TecnicoUserDetailsService.java` | Carga transaccional por username y planta para autenticar |
| `security/SecurityConfig.java` | Rutas públicas/administrativas, Basic, orden de filtros, CORS y BCrypt 12 |
| `model/Tecnico.java` | Identidad, hash, unicidad por planta y baja lógica |
| `model/Falla.java` | Agrupación de participantes de la misma avería |
| `model/EventoLoto.java` | Eventos append-only y protección complementaria de PostgreSQL |
| `model/Rol.java` | Perfiles cerrados TECNICO y SUPERVISOR |
| `model/Maquina.java` | Contadores digitales, MTBF nullable y baja lógica |
| `repository/TecnicoRepository.java` | Consultas parametrizadas y SELECT FOR UPDATE del técnico |
| `repository/FallaRepository.java` | Búsqueda de referencia bajo lock previo de máquina |
| `repository/EventoLotoRepository.java` | Interfaz limitada a guardar y consultar; sin métodos de borrado |
| `service/TecnicoService.java` | Administración, restricciones de baja y hash con límite UTF-8 |
| `service/BloqueoService.java` | Identidad, locks, falla compartida, notas normalizadas y transiciones auditadas |
| `service/MaquinaService.java` | Catálogo, permisos administrativos, mediciones y baja lógica |
| `controller/TecnicoController.java` | Adaptación HTTP y delegación al servicio |
| `dto/TecnicoCreateDTO.java` | Entrada inmutable; toString excluye la contraseña |
| `dto/TecnicoUpdateDTO.java` | Entrada de actualización; toString redactado |
| `dto/TecnicoResponseDTO.java` | Salida sin contraseña ni hash |
| `dto/EventoLotoResponseDTO.java` | Contrato de trazabilidad sin notas confidenciales |
| `dto/BloqueoCreateDTO.java` | Declaración de candado, referencia y campos que no acreditan identidad |
| `exception/GlobalExceptionHandler.java` | Códigos HTTP existentes y respuestas sin SQL ni trazas internas |

## Invariantes para futuras modificaciones

- Arquitectura monolítica multicapa y modelo de cinco tablas.
- Mismas rutas `/api/v1/...`, verbos, nombres y orden de componentes de DTO.
- Firmas públicas de Controller, Service, Repository y Model conservadas.
- HTTP Basic individual y `BCryptPasswordEncoder(12)` conservados.
- Orden de adquisición de locks LOTO: **Técnico → Máquina → Ticket**.
- En la liberación, consultar primero `findMaquinaId` sin cargar la entidad del
  ticket, después bloquear la máquina y finalmente el ticket. Se conserva la
  comprobación de propietario y de estado antes de descontar el contador.
- `PlantaTransactionAspect` conserva `set_config(..., true)`, el parámetro
  enlazado y los órdenes del aspecto y del interceptor transaccional.
- SQL, migraciones, pom.xml y application.properties sin cambios en esta revisión.
- Los dos DTO de contraseñas siguen ocultando su contraseña en `toString`.
- Ninguna nota confidencial se agrega a logs ni al historial de eventos.

## Verificación y reproducibilidad

Se utilizó JavaParser 3.28.2 para comprobar la estructura Java y
Google Java Format 1.28.0 con estilo de cuatro espacios para el formato. Los
records se ajustaron para mostrar sus anotaciones/componentes por separado.
Son herramientas auxiliares de esta revisión: no se añadieron al pom.xml.
Se verificaron los checksums oficiales de sus artefactos descargados.

Antes del cambio de notas, se comprobó equivalencia de la estructura ejecutable
de las 24 clases, ignorando comentarios, orden de imports, llaves equivalentes
y partición de literales en concatenaciones constantes por el formateador.
Después, se repitió la comparación permitiendo diferencias únicamente en el
cuerpo de `crearBloqueo`; también se conservó su firma y sus anotaciones.
Esto acota el cambio funcional, pero no reemplaza las pruebas.

Pruebas añadidas en `SilbomApplicationTests`:

- POST y GET autorizado devuelven el mismo texto normalizado después de pasar
  por persistencia y descifrado.
- Cuatro casos parametrizados: `null`, cadena vacía, espacios/controles y
  etiquetas sin texto. Todos deben responder `null` en alta y consulta.

Comandos convencionales:

```bash
mvn clean test
bash scripts/test-postgres.sh
```

En la nube se activa el JDK con `/workspace/.silbom-cloud/activate.sh` y se pasa a
Maven el archivo de ajustes de proxy mediante `-s`, con caché fuera del checkout.
El segundo comando requiere el rol real de aplicación y un administrador de
pruebas para preparar su propia base efímera; nunca apunta a producción.

### Resultados de esta ejecución

| Comprobación | Resultado actual |
|---|---|
| `mvn clean test` con ajustes de proxy del entorno | BUILD SUCCESS: 42 pruebas; 0 fallos, 0 errores, 0 omitidas |
| `scripts/test-postgres.sh` → `mvn -Ppostgres-it verify` | BUILD SUCCESS: 42 pruebas en memoria + 7 PostgreSQL; 49 casos en total |
| Casos anteriores | Las 37 pruebas originales y las 7 PostgreSQL siguen pasando |
| Regresiones nuevas | Cinco casos de normalización/ausencia de notas pasados |
| Comparación estructural Java | 24 clases verificadas; se permite cambiar solo el cuerpo de crearBloqueo |
| SQL, migración, POM y configuración principal | Sin diferencias contra el commit base |
| `.env` y `target/` | Ignorados por Git y excluidos de los archivos versionados |

El auxiliar PostgreSQL creó su base efímera, aplicó dos veces el esquema,
comprobó la eliminación de la política antigua y ejecutó los siete casos reales
con el rol no propietario. La base efímera se eliminó al terminar. No se migró
la base de desarrollo ni se modificó producción durante esta revisión.

Los reportes XML de esta ejecución están en `target/surefire-reports` y
`target/failsafe-reports`. El segundo comando vuelve a ejecutar la suite de
memoria como parte de verify; los 49 son casos distintos de la ejecución integrada,
no una suma artificial de las dos ejecuciones. Los logs locales se conservan
fuera del repositorio y no contienen valores en este documento.

## Fuera de alcance

Esta revisión no conecta el frontend, no cambia la autenticación, no agrega
nuevas tablas ni migra registros históricos. No sustituye la auditoría de CVE ni
resuelve la política de red que bloqueó antes la API GitHub. Las limitaciones de
TLS/CSRF, QR/NFC y período de medición del MTBF siguen en los informes anteriores.

Los resultados anteriores de Newman pertenecen a otra ejecución: no deben
presentarse como una nueva demostración de esta revisión si no se vuelve a correr.

## Archivos adicionales modificados

- `README.md`: enlace al registro de esta auditoría para localizarlo desde el proyecto.
- `src/test/resources/application-test.properties`: eliminación de una propiedad obsoleta.
- `src/test/java/cl/silbom/backend/SilbomApplicationTests.java`: cinco casos de regresión.
- `docs/REGISTRO_AUDITORIA_GEMINI.md`: este registro técnico y guía de continuidad.

## Documentos relacionados

- [Resumen de las mejoras anteriores](MEJORAS_BACKEND.md).
- [Auditoría previa del backend](auditoria-backend.md).
- [Matriz de controles](controles-seguridad.md).
- [Instalación y API](../README.md).
