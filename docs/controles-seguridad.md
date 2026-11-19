# Controles de seguridad: implementación y evidencia

La matriz reemplaza la afirmación original de «20 controles garantizados».
No constituye certificación legal, industrial ni auditoría de pentesting.

| # | Control | Estado real y evidencia |
|---|---|---|
| 1 | Credenciales | .env ignorado y variables externas; bootstrap sin contraseña predeterminada. No se devuelve ni registra la contraseña. |
| 2 | Secretos en Git | Revisión de archivos y comparación con credenciales locales antes de publicar. Fixtures de test son públicas y exclusivas de prueba. |
| 3 | Rol DB mínimo | PostgreSQL IT exige rol no propietario, sin superusuario/bypass. Sin DDL ni DELETE físico; migrador separado. |
| 4 | RLS | SET LOCAL en la misma conexión/transacción; cinco tablas con ENABLE/FORCE. PostgreSQL IT verifica otra planta, falta de contexto, escritura y limpieza del pool. El poseedor del rol DB compartido puede cambiar su contexto: no equivale a roles separados por cliente. |
| 5 | Cifrado | AES-256-GCM, IV aleatorio; tests de round-trip, IV distinto y manipulación; PostgreSQL comprueba notas cifradas. Rotación y backups requieren procedimiento operacional. |
| 6 | Autenticación | Basic individual; credenciales inválidas/inactivas y API key antigua reciben 401. TLS fuera de loopback corresponde al despliegue. |
| 7 | Autorización/IDOR | Supervisor para administración; responsable para liberar; filtro de planta; notas solo para propietario/supervisor. Tests HTTP con dos identidades. No se libera un ticket ajeno mediante privilegios de supervisor. |
| 8 | Mass assignment | DTOs; responsable tomado de autenticación, no JSON. Estados, planta y contadores no editables en DTO de bloqueo. Prueba de nombre falsificado. |
| 9 | Cookies | API stateless sin cookies de autenticación; sus atributos no constituyen un control activo de login. |
| 10 | Hash | BCrypt costo 12 usado en usuarios y login. Máximo 72 bytes UTF-8 para evitar truncamiento. Tests con credenciales correctas/incorrectas. |
| 11 | Rate limiting | 60 lecturas/20 escrituras por IP/minuto/proceso; 429/Retry-After probado. No distribuido; IP compartida por NAT también comparte cuota. |
| 12 | Bots | UA obligatorio, tres agentes conocidos y honeypot. Heurísticas falsificables, no CAPTCHA ni protección completa de bots/DDoS. |
| 13 | Consultas | JPQL y contexto RLS parametrizados. SQL estático de esquema fuera de peticiones. |
| 14 | Validación | DTO, normalización, valores finitos, rangos, referencia de falla, notas limitadas y reglas de dominio. Pruebas de entradas y división por cero. |
| 15 | Contenido | Texto visible normalizado y sin controles. Regex no es un sanitizador HTML general. El futuro frontend debe escapar según contexto y usar textContent. |
| 16 | Archivos | Multipart deshabilitado; no existe API de subida. No se declara implementada validación de firmas de archivos. |
| 17 | Respuestas | DTOs sin hashes/contraseñas; errores sin SQL/trazas; notas según permiso. |
| 18 | Headers/CORS | Lista cerrada de orígenes; tests permitido/prohibido, headers y 401. CORS no reemplaza autenticación. |
| 19 | HTTPS | HSTS solo con request seguro. No se incluye certificado ni terminación TLS ni se afirma que HSTS fuerce HTTPS por sí solo. |
| 20 | Dependencias | Boot actualizado a 3.5.16 y árbol Maven disponible. Consulta externa de CVE/soporte bloqueada por proxy 403; no se afirma ausencia de vulnerabilidades ni soporte vigente sin confirmación oficial. |

CREADO/LIBERADO se registran en la misma transacción del ticket, con identidad y
fecha. SQL impide modificar/borrar eventos y solo permite ACTIVO→CERRADO en
tickets; el rol de aplicación carece de DELETE. Los legados se marcan
LEGADO_IMPORTADO sin inventar identidad. Los eventos no contienen contraseñas,
Authorization ni notas. No hay SIEM ni auditoría de todo cambio administrativo.
