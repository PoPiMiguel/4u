#!/usr/bin/env bash
# Sube la versión en todo el proyecto, hace commit y tag, y empuja.
# El tag vX.Y.Z lanza .github/workflows/release.yml (instaladores, APK y latest.json).
#   scripts/release.sh 0.3.0
set -euo pipefail
cd "$(dirname "$0")/.."

v="${1:-}"
[[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Uso: $0 X.Y.Z" >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Hay cambios sin commit" >&2; exit 1; }
git rev-parse "v$v" >/dev/null 2>&1 && { echo "El tag v$v ya existe" >&2; exit 1; }

# Fuente única: tauri.conf.json (Android la lee de ahí al compilar).
sed -i -E "0,/\"version\": \"[0-9.]+\"/s//\"version\": \"$v\"/" desktop/src-tauri/tauri.conf.json
sed -i -E "0,/^version = \"[0-9.]+\"/s//version = \"$v\"/" desktop/src-tauri/Cargo.toml
sed -i -E "0,/\"version\": \"[0-9.]+\"/s//\"version\": \"$v\"/" desktop/package.json
(cd desktop && npm install --package-lock-only --ignore-scripts >/dev/null)
(cd desktop/src-tauri && cargo update -p fouru --offline >/dev/null 2>&1 || cargo generate-lockfile >/dev/null)

git add desktop/src-tauri/tauri.conf.json desktop/src-tauri/Cargo.toml desktop/src-tauri/Cargo.lock \
        desktop/package.json desktop/package-lock.json
git commit -m "Versión $v"
git tag -a "v$v" -m "4u $v"
git push origin HEAD "v$v"
echo "Publicando v$v: sigue el progreso con 'gh run watch'."
