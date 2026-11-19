#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ANDROID_SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BUILD_TOOLS="$ANDROID_SDK/build-tools/35.0.0"
PLATFORM_JAR="$ANDROID_SDK/platforms/android-34/android.jar"

if [[ ! -f "$PLATFORM_JAR" ]]; then
  echo "ERROR: No se encontró $PLATFORM_JAR" >&2
  exit 1
fi

BUILD_DIR="$SCRIPT_DIR/build"
GEN_DIR="$BUILD_DIR/gen"
OBJ_DIR="$BUILD_DIR/obj"
DEX_DIR="$BUILD_DIR/dex"
RES_ZIP="$BUILD_DIR/res.zip"
UNALIGNED_APK="$BUILD_DIR/silbom-tecnico-unaligned.apk"
ALIGNED_APK="$BUILD_DIR/silbom-tecnico-aligned.apk"
FINAL_APK="$BUILD_DIR/silbom-tecnico.apk"
KEYSTORE="${SILBOM_DEBUG_KEYSTORE:-$HOME/.android/silbom-debug.keystore}"

rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$OBJ_DIR" "$DEX_DIR"

echo "[1/6] Compilando recursos Android con aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$SCRIPT_DIR/res" -o "$RES_ZIP"

echo "[2/6] Enlazando APK base y assets..."
"$BUILD_TOOLS/aapt2" link \
  -I "$PLATFORM_JAR" \
  --manifest "$SCRIPT_DIR/AndroidManifest.xml" \
  -o "$UNALIGNED_APK" \
  -A "$SCRIPT_DIR/assets" \
  -R "$RES_ZIP" \
  --java "$GEN_DIR" \
  --auto-add-overlay

echo "[3/6] Compilando código Java..."
javac \
  --release 8 \
  -cp "$PLATFORM_JAR" \
  -d "$OBJ_DIR" \
  "$GEN_DIR/cl/silbom/tecnico/R.java" \
  "$SCRIPT_DIR/src/cl/silbom/tecnico/NfcTextDecoder.java" \
  "$SCRIPT_DIR/src/cl/silbom/tecnico/MainActivity.java"

echo "[4/6] Convirtiendo bytecode a Dalvik (classes.dex)..."
"$BUILD_TOOLS/d8" \
  --min-api 24 \
  --lib "$PLATFORM_JAR" \
  --output "$DEX_DIR" \
  $(find "$OBJ_DIR" -name "*.class")

echo "[5/6] Empaquetando classes.dex y alineando APK..."
(cd "$DEX_DIR" && zip -uj "$UNALIGNED_APK" classes.dex >/dev/null)
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNALIGNED_APK" "$ALIGNED_APK"

echo "[6/6] Generando firma debug y verificando APK..."
if [[ ! -f "$KEYSTORE" ]]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  umask 077
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=SILBOM Tecnico,O=Universidad Mayor,C=CL" >/dev/null 2>&1
  echo "Nuevo certificado DEBUG creado en $KEYSTORE (fuera del repositorio)."
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --ks-key-alias androiddebugkey \
  --key-pass pass:android \
  --out "$FINAL_APK" \
  "$ALIGNED_APK"

"$BUILD_TOOLS/apksigner" verify --verbose "$FINAL_APK"

echo "APK Debug generado (NO se copia a GitHub ni a recursos web):"
echo "  - $FINAL_APK"
echo "Para instalar sobre una versión anterior, conserva $KEYSTORE."
