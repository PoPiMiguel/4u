#!/usr/bin/env bash
# Sube a GitHub los secretos que necesita .github/workflows/release.yml.
# Los lee de archivos locales (fuera del repo) y los pasa por stdin a `gh`.
#   scripts/ci-secrets.sh
set -euo pipefail
cd "$(dirname "$0")/.."
R="PoPiMiguel/4u"
S="$HOME/.secrets"

getenv() { grep -E "^$1=" desktop/.env | head -1 | cut -d= -f2- | tr -d "\"'"; }

getenv VITE_SUPABASE_URL      | gh secret set SUPABASE_URL -R "$R"
getenv VITE_SUPABASE_ANON_KEY | gh secret set SUPABASE_ANON_KEY -R "$R"
base64 -w0 android/app/google-services.json | gh secret set GOOGLE_SERVICES_JSON_B64 -R "$R"
base64 -w0 "$S/4u-release.jks"              | gh secret set ANDROID_KEYSTORE_B64 -R "$R"
gh secret set ANDROID_KEYSTORE_PASSWORD -R "$R" < "$S/4u-release.pass"
printf '4u' | gh secret set ANDROID_KEY_ALIAS -R "$R"
gh secret set TAURI_SIGNING_PRIVATE_KEY          -R "$R" < "$S/4u-updater.key"
gh secret set TAURI_SIGNING_PRIVATE_KEY_PASSWORD -R "$R" < "$S/4u-updater.pass"
gh secret list -R "$R"
