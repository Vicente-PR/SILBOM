# SILBOM Android NFC — pruebas y entrega

## Estado de esta rama
Esta rama integra lector NFC **real** en la aplicación Android WebView existente
(`android-app/src/cl/silbom/tecnico/MainActivity.java`). No requiere Capacitor:
la UI existente se reutiliza y el lector nativo transmite exclusivamente el
identificador a JavaScript. La consulta de máquina usa
`GET /api/v1/maquinas/{id}` contra la API configurada.

**No confundir** compilación o pruebas unitarias con una prueba de lectura NFC
física. El teléfono Android y las etiquetas físicas se deben probar en terreno.

## Etiquetas físicas: un código NDEF Text por etiqueta

| Etiqueta | Texto NDEF exacto | Máquina |
|---|---|---|
| NFC 1 | `EQ-COM-03` | Compresor de Tornillo |
| NFC 2 | `EQ-ENV-01` | Envasadora Rotativa Automática |
| NFC 3 | `EQ-EXT-02` | Extrusora de Polímeros |

En **NFC Tools**: Escribir → Añadir un registro → Texto → ingresar
el código → **dejar exactamente un registro** → Escribir en un sticker
distinto → Leer para verificar. No usar URL ni UID físico.
Las etiquetas comunes pueden fallar sobre metal; probar separadas de la
carcasa y elegir etiquetas on-metal para uso en maquinaria.

## Preparación del sistema

1. Iniciar PostgreSQL y Spring Boot en el computador con base de **pruebas**.
2. Configurar una dirección **HTTPS confiable** para el backend accesible
   desde el teléfono. Las credenciales Basic sobre HTTP no son seguras.
   No desactivar controles de seguridad del backend.
3. Conectar Android y computador a una red que permita comunicación.
4. Instalar APK de desarrollo, abrir SILBOM y probar la conexión con el servidor.
5. Confirmar que cada máquina ya está registrada en PostgreSQL. También
   se puede comprobar con Postman: `GET /api/v1/maquinas/EQ-COM-03`.

## Prueba de lectura física

1. Activar NFC en Ajustes de Android.
2. Abrir SILBOM → Operar → **Escanear etiqueta NFC**.
3. Acercar sticker `EQ-COM-03` al lector NFC del teléfono.
4. El lector debe obtener el registro NDEF Text, decodificarlo y comunicar
   exclusivamente `EQ-COM-03` a la interfaz.
5. La interfaz consulta la API individual y refresca el catálogo **confirmado**.
6. Solo ante respuesta válida abre la ficha de `EQ-COM-03`.
7. Repetir con las etiquetas `EQ-ENV-01` y `EQ-EXT-02`.
8. Probar lectura cancelada, NFC desactivado, etiqueta con texto inválido,
   etiqueta con varios textos, servidor apagado y código inexistente.

**Resultados esperados:** no se abre una ficha ni se habilitan operaciones
LOTO basadas en valores de demostración si falla la API; no se crea ni se
libera ningún ticket por leer NFC. La ausencia de bloqueos digitales no
autoriza reenergizar equipos.

## Pruebas de código puras (sin Android SDK)

Desde la raíz del repositorio:

```bash
bash tests/android-nfc/run-tests.sh
```

Incluyen formato de identificadores, rutas `/maquinas/{id}`,
NDEF Text UTF-8/UTF-16 y prefijos de idioma `en`/`es`.

## Construcción de APK Debug

Requiere JDK, `keytool`, Java y Android SDK con API 34 y
Build Tools 35.0.0 en `ANDROID_SDK_ROOT` (o `~/Android/Sdk`):

```bash
bash tests/android-nfc/run-tests.sh
bash android-app/build-apk.sh
```

APK esperado: `android-app/build/silbom-tecnico.apk`.
La clave debug persiste en `~/.android/silbom-debug.keystore`
y **no se copia** al repositorio. Guardar esa clave local
para poder actualizar el mismo paquete debug en el teléfono.

Opcional (Android conectado por USB con depuración):
```bash
adb install -r android-app/build/silbom-tecnico.apk
adb logcat -s ActivityManager Chromium NfcService
```

La rama no lleva APK, certificados ni secretos binarios.

## Límites y seguridad antes de entregar

- El frontend WebView heredado sigue utilizando recursos `file://` con
  acceso universal a URLs; el puente nativo solo debería usarse en
  contenido local de confianza. Migrar a `WebViewAssetLoader` y endurecer
  políticas de navegación/CSP antes de producción.
- Verificar la política de origen/HTTPS del cliente de la rama corregida;
  no transmitir contraseñas reales por HTTP local.
- Sin Android SDK no se puede certificar el armado de la APK. Sin teléfono
  compatible no se puede certificar lectura NFC real.
- Un código grabado en un sticker puede clonarse: sirve **solo para
  identificar** la máquina, no para autenticar usuarios ni verificar
  aislamiento físico o cumplimiento normativo.
