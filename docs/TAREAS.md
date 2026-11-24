# Tareas manuales (las tienes que hacer tú)

Cosas que no se pueden automatizar desde el código: cuentas, claves y permisos. Ve marcando según avances.
Los comandos detallados están en [SETUP-ARCH.md](SETUP-ARCH.md).

> ⚠️ **Nunca subas a git**: `.env`, `local.properties`, `google-services.json`, el JSON de la cuenta de servicio de Firebase ni los keystores. El `.gitignore` ya los excluye; no lo fuerces con `git add -f`.

## 1. Supabase (backend) — gratis, sin tarjeta

- [ ] Crear cuenta en https://supabase.com (vale con GitHub).
- [ ] **New project**:
  - Nombre: `4u`
  - Región: **Central EU (Frankfurt)** o **West EU (Ireland)**
  - Contraseña de la base de datos: genera una fuerte y **guárdala** (gestor de contraseñas).
  - Plan: **Free**.
- [ ] Apuntar en tu gestor de contraseñas (Project Settings → API / Data API):
  - **Project URL** → `https://xxxx.supabase.co`
  - **Project ref** → el `xxxx` de la URL
  - **anon / publishable key** → va en las apps (es pública por diseño; la seguridad la dan las políticas RLS)
  - ❌ La **service_role / secret key** NO va en las apps nunca.
- [ ] Authentication → Sign In / Providers → **Email**: desactivar **"Confirm email"**.
  Motivo: el correo integrado de Supabase está muy limitado en el plan gratis. Ver [DECISIONS.md](DECISIONS.md#1-login).
- [ ] Generar un secreto para las funciones: `openssl rand -hex 32` → guardarlo como `FUNCTIONS_SECRET`.
- [ ] (Tras `db push`, paso 4 de SETUP) Ejecutar en **SQL Editor** el bloque de Vault de [SETUP-ARCH.md](SETUP-ARCH.md#4-supabase-desplegar-backend).

## 2. Firebase (solo para las notificaciones de Android) — gratis, sin tarjeta

- [ ] Entrar en https://console.firebase.google.com con tu cuenta de Google.
- [ ] **Crear proyecto** `4u` → desactivar Google Analytics (no hace falta). Se queda en plan **Spark** (gratis). No lo pases a Blaze.
- [ ] Dentro del proyecto → **Añadir app → Android**:
  - Nombre del paquete: **`app.fouru`** (exacto)
  - Apodo: `4u`
  - SHA-1: dejar vacío
- [ ] Descargar **`google-services.json`** → copiarlo a `android/app/google-services.json`.
- [ ] Project settings → **Service accounts** → **Generate new private key** → se descarga un JSON.
  - Guárdalo fuera del repo, p. ej. `~/.secrets/4u-fcm.json`.
  - Se sube a Supabase como secreto `FCM_SERVICE_ACCOUNT` (SETUP paso 4).
- [ ] Comprobar que **Cloud Messaging API (V1)** aparece como habilitada (Project settings → Cloud Messaging). Si pone "deshabilitada", pulsar el enlace y habilitarla.

## 3. GitHub

- [ ] El repo ya existe (`PoPiMiguel/4u`). Decidir si **público** (Actions gratis e ilimitado) o **privado** (2000 min/mes gratis, de sobra).
- [ ] Cuando llegue el M7 (releases), añadir en Settings → Secrets and variables → Actions:
  - `SUPABASE_URL`, `SUPABASE_ANON_KEY`
  - `GOOGLE_SERVICES_JSON_B64` → `base64 -w0 android/app/google-services.json`
  - (más adelante) keystore de firma Android y clave del updater de Tauri

## 4. En tu Arch

- [ ] Instalar herramientas → [SETUP-ARCH.md](SETUP-ARCH.md#1-paquetes-del-sistema).
- [ ] Móvil Android: activar **Opciones de desarrollador** (tocar 7 veces "Número de compilación") → **Depuración USB**.
- [ ] Móvil de tu novia: más adelante le pasas el APK. Tendrá que permitir "Instalar apps desconocidas" para el navegador o el gestor de archivos.

## 5. Antes de usarlo "en serio"

- [ ] Probar el flujo completo con dos cuentas (tú y una de prueba) antes de dárselo a tu novia.
- [ ] Panel de Supabase → **Reports**: echar un ojo al uso de Storage y egress la primera semana.
