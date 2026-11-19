# Decisiones de Arquitectura: Plataforma Móvil y Distribución (Hito 3)

Este documento registra dos decisiones clave tomadas para la entrega del Hito 3 en la asignatura Arquitectura de Desarrollo (Móvil y Web), listas para su integración en el informe final.

---

## 1. Limitación Declarada: Exclusión de Soporte para Plataforma iOS (Criterio B1)

Durante el diseño e implementación del cliente móvil para técnicos en terreno de SILBOM, el equipo evaluó la viabilidad de entregar soporte multiplataforma (Android e iOS). Tras contrastar los requisitos técnicos, económicos y operacionales, se resolvió descartar de forma explícita el soporte para dispositivos iOS (iPhone / iPad), asumiendo el costo técnico y funcional de esta decisión.

Las razones que fundamentaron esta limitación fueron las siguientes:

1. **Restricciones de entorno y herramientas de desarrollo:** El empaquetado y compilación de binarios para iOS requiere de forma obligatoria estaciones de trabajo con hardware macOS y la suite Xcode. El equipo de trabajo contó con equipos de desarrollo basados en Linux (Ubuntu) y Windows, lo que impedía generar y firmar aplicaciones nativas para el ecosistema de Apple de manera reproducible en el repositorio.
2. **Barrera económica y licenciamiento:** La distribución, incluso para pruebas internas o desarrollo, exige una suscripción activa al *Apple Developer Program* con un costo anual de \$99 USD, además de certificados de firma digital y aprovisionamiento vinculados a cuentas de pago.
3. **Imposibilidad de instalación directa en terreno (*sideloading*):** A diferencia del ecosistema Android, iOS no permite la instalación libre de instaladores externos sin pasar por el proceso de revisión de la App Store o mediante la plataforma TestFlight (que añade tiempos de espera de revisión y expiración de versiones).
4. **Contexto operativo en plantas industriales:** En el sector de manufactura, alimentos y plantas de proceso en Chile (como el contexto modelado para la Planta San Joaquín), el equipamiento asignado a los operadores de mantenimiento se compone predominantemente de terminales móviles rugerizados (equipos industriales de uso rudo tipo Zebra, Honeywell o similares) y smartphones corporativos, los cuales operan de manera prácticamente absoluta bajo el sistema operativo Android. Estos terminales cuentan con soporte nativo para lectura NFC (chips NXP/Ndef) y permiten la instalación directa y controlada de paquetes APK corporativos.

**Resolución técnica:** Se consolidó el desarrollo móvil con un enfoque 100% centrado en Android (versión mínima 7.0 / SDK 24, probado hasta Android 14 / SDK 34), asegurando lectura NFC de etiquetas en terreno con respaldo de código QR y entrada manual de identificador de equipo.

---

## 2. Bitácora de Decisiones: Distribución mediante GitHub Releases y Limpieza de Git (Criterio B2)

| Campo | Detalle |
|---|---|
| **Fecha de decisión** | 12 de noviembre de 2026 |
| **Hito del proyecto** | Hito 3 (Integración Full-Stack y Producto Funcional) |
| **Responsables** | Equipo de Desarrollo SILBOM |
| **Estado** | Aprobada e implementada |

### Contexto y problema detectado
En una primera instancia de empaquetado de la aplicación móvil de terreno (`SILBOM Técnico`), se generó el binario ejecutable `silbom-tecnico.apk` y se almacenó temporalmente dentro del árbol del repositorio en las carpetas `docs/` y `src/main/resources/static/`. 

Al revisar las reglas del proyecto universitario (Anexo B: Reglas del repositorio), se constató que versionar archivos binarios compilados dentro del árbol de trabajo de Git genera los siguientes problemas de ingeniería:
- Incremento innecesario del peso del repositorio Git en cada actualización del instalador.
- Imposibilidad de realizar seguimiento de diferencias textuales (*diff*) sobre archivos binarios.
- Riesgo de conflictos de fusión (*merge conflicts*) no resolubles en el control de versiones.
- Vulneración del principio de higiene que exige mantener el repositorio exclusivamente para código fuente, scripts de despliegue y documentación en texto plano o PDF.

### Alternativas evaluadas
1. **Conservar el binario en la rama principal:** Descartada por contravenir el Anexo B de la asignatura y buenas prácticas de ingeniería de software.
2. **Servir el APK únicamente a través del backend Spring Boot:** Se implementó como endpoint estático local para desarrollo (`/silbom-tecnico.apk`), pero obligaba a mantener el archivo físico dentro de los recursos estáticos del empaquetado del servidor.
3. **Distribuir el APK a través de GitHub Releases:** Aprobada. Permite alojar binarios compilados de forma externa a las ramas de código, vinculados a etiquetas de versión (*tags*), con enlaces estables de descarga para los usuarios y evaluadores.

### Decisión tomada e impacto
1. **Desvinculación del control de versiones:** Se ejecutó `git rm --cached` sobre los archivos `docs/silbom-tecnico.apk` y `src/main/resources/static/silbom-tecnico.apk`. Los archivos físicos se mantuvieron en el disco local para pruebas locales, pero dejaron de ser rastreados por Git.
2. **Aislamiento en `.gitignore`:** Se actualizaron las reglas de exclusión en `.gitignore` para bloquear cualquier artefacto binario móvil futuro (`*.apk`, `*.aab`, `build/` y `android-app/build/`), respetando la prohibición de reescribir el historial o aplicar empujes forzados (`git push --force`).
3. **Distribución en GitHub Releases:** Se adoptó la pestaña de Releases del repositorio (`https://github.com/Bvstivn-A/Silbom_Pruebas/releases`) como canal oficial de entrega del instalador Android, iniciando con una etiqueta de **Pre-release** para pruebas de validación con técnicos y docentes mientras se consolidan los últimos ajustes previos a la versión definitiva `v1.0.0`.
