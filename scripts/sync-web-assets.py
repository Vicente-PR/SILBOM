#!/usr/bin/env python3
"""Propaga las fuentes web canónicas sin copiar datos de ejecución ni credenciales."""
import argparse
from pathlib import Path

root = Path(__file__).resolve().parents[1]
static = root / "src/main/resources/static"
mobile = (static / "app/index.html").read_text()
panel = (static / "index.html").read_text()
client = (static / "app/api-client.js").read_text()
nfc_helper = (static / "app/nfc-code.js").read_text()
copies = {
    "src/main/resources/static/Silbom1.html": panel,
    "docs/Silbom1.html": panel.replace('./app/api-client.js', './api-client.js'),
    "src/main/resources/static/silbom-tecnico.html": mobile.replace('./api-client.js', './app/api-client.js')
        .replace('./manifest.webmanifest', './app/manifest.webmanifest').replace('./icons/', './app/icons/')
        .replace('./nfc-code.js', './app/nfc-code.js'),
    "docs/silbom-tecnico.html": mobile.replace('./manifest.webmanifest', '../src/main/resources/static/app/manifest.webmanifest')
        .replace('./icons/', '../src/main/resources/static/app/icons/'),
    "docs/api-client.js": client,
    "docs/nfc-code.js": nfc_helper,
    "android-app/assets/index.html": mobile,
    "android-app/assets/api-client.js": client,
    "android-app/assets/nfc-code.js": nfc_helper,
}
check = argparse.ArgumentParser()
check.add_argument('--check', action='store_true')
args = check.parse_args()
different = []
for path, content in copies.items():
    target = root / path
    if not target.exists() or target.read_text() != content:
        different.append(path)
        if not args.check:
            target.write_text(content)
if args.check and different:
    raise SystemExit('Copias desactualizadas: ' + ', '.join(different))
print('Fuentes web sincronizadas.' if not args.check else 'Copias web verificadas.')
