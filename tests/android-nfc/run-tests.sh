#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
node --test "$ROOT/tests/android-nfc/nfc-code.test.js"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
javac -d "$TEMP_DIR" \
  "$ROOT/android-app/src/cl/silbom/tecnico/NfcTextDecoder.java" \
  "$ROOT/tests/android-nfc/NfcTextDecoderTest.java"
java -cp "$TEMP_DIR" cl.silbom.tecnico.NfcTextDecoderTest
