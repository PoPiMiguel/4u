# Puesta en marcha en Arch Linux (Hyprland)

Orden recomendado. Antes, ten hechas las cuentas de [TAREAS.md](TAREAS.md) (apartados 1 y 2).

> **Nota:** El código está revisado contra las APIs de las versiones fijadas, pero se escribió sin compilarlo (en el PC del trabajo no hay herramientas instaladas).
> Si el primer build se queja de algo, la lista de puntos a revisar está en [TODO.md](TODO.md#m0--base).

## 1. Paquetes del sistema

```bash
sudo pacman -Syu
sudo pacman -S --needed git base-devel curl wget file openssl \
  nodejs npm rustup \
  webkit2gtk-4.1 librsvg libayatana-appindicator xdotool \
  android-tools android-udev ffmpeg

rustup default stable
```

- `webkit2gtk-4.1`, `librsvg`, `xdotool` → Tauri en Linux.
- `libayatana-appindicator` → icono en la bandeja (Waybar necesita el módulo `tray`). Si falla, prueba `libappindicator-gtk3` desde AUR.
- `android-tools` (adb) y `android-udev` → depurar con el móvil por USB.

Android Studio (AUR):

```bash
yay -S android-studio      # o paru
```

Primer arranque de Android Studio: deja que instale el **SDK**, las **Platform-Tools** y un **JDK** (trae el suyo).

### Ajustes para Hyprland

En `~/.config/hypr/hyprland.conf`:

```ini
# Las apps Java (Android Studio) salen en blanco en gestores de ventanas tipo tiling sin esto
env = _JAVA_AWT_WM_NONREPARENTING,1
```

Si la ventana de Tauri sale en blanco (típico con NVIDIA y Wayland), arranca con:

```bash
WEBKIT_DISABLE_DMABUF_RENDERER=1 npm run tauri dev
```

## 2. Clonar

```bash
git clone https://github.com/PoPiMiguel/4u.git
cd 4u
```

## 3. Supabase CLI

No hace falta instalarlo globalmente; se usa con `npx`.

```bash
npx supabase login                         # abre el navegador
npx supabase init                          # crea supabase/config.toml (respeta migrations/ y functions/)
npx supabase link --project-ref TU_REF     # pide la contraseña de la BD
```

Después de `init`, añade al final de `supabase/config.toml`:

```toml
[functions.notify]
verify_jwt = false

[functions.purge]
verify_jwt = false
```

Motivo: las llama la propia base de datos con el secreto `x-4u-secret`, no un usuario con JWT.

## 4. Supabase: desplegar backend

```bash
# Tablas, RLS, RPCs, cron y bucket de Storage
npx supabase db push

# Secretos de las Edge Functions
npx supabase secrets set FUNCTIONS_SECRET=EL_SECRETO_QUE_GENERASTE
npx supabase secrets set FCM_SERVICE_ACCOUNT="$(cat ~/.secrets/4u-fcm.json)"

# Funciones (--use-api evita necesitar Docker)
npx supabase functions deploy notify --use-api
npx supabase functions deploy purge --use-api
```

Si `--use-api` no existe en tu versión del CLI: `sudo pacman -S docker && sudo systemctl start docker` y despliega sin esa opción.

Ahora, en el panel → **SQL Editor**, ejecuta **una vez**. No se guarda en el repo porque son secretos:

```sql
select vault.create_secret('https://TU_REF.supabase.co', 'project_url');
select vault.create_secret('EL_SECRETO_QUE_GENERASTE', 'functions_secret');
```

Comprobaciones:
- Table Editor: existen `profiles`, `contacts`, `messages`…
- Storage: existe el bucket `media` (privado).
- Integrations → Cron: jobs `4u-dispatch-scheduled` y `4u-purge`.

## 5. App de escritorio (Tauri)

```bash
cd desktop
cp .env.example .env        # rellenar URL y anon key
npm install
npx tauri icon ../assets/logo.svg    # genera src-tauri/icons/* (obligatorio antes del primer build)
npm run tauri dev
```

El primer `tauri dev` compila Rust y tarda unos minutos. Después es rápido.

Build de instaladores:

```bash
npm run tauri build      # en Linux: .AppImage y .deb en src-tauri/target/release/bundle/
```

El `.msi`/`.exe` de Windows se genera en GitHub Actions (ver `.github/workflows/release.yml`) o compilando en un Windows.

### Widget en Hyprland (fase 1)

Hyprland ignora "alwaysOnBottom". Usa reglas de ventana. Averigua primero la clase real:

```bash
hyprctl clients | grep -A6 "4u widget"
```

Ejemplo en `hyprland.conf`. **La sintaxis cambia entre versiones de Hyprland: revisa la página "Window Rules" de la wiki si no te la acepta.**

```ini
windowrulev2 = float,    title:^(4u widget)$
windowrulev2 = pin,      title:^(4u widget)$
windowrulev2 = noborder, title:^(4u widget)$
windowrulev2 = noshadow, title:^(4u widget)$
windowrulev2 = nofocus,  title:^(4u widget)$
windowrulev2 = move 20 60, title:^(4u widget)$
```

Fase 2 (M8): capa `background` con layer-shell + módulo de Waybar. Ver [TODO.md](TODO.md#m8--hyprland).

## 6. App Android

```bash
cp android/local.properties.example android/local.properties   # rellenar URL y anon key
# copiar google-services.json a android/app/
```

1. Android Studio → **Open** → carpeta `android/`.
2. Si avisa de que **falta el Gradle wrapper**, acepta crearlo o, desde terminal:
   ```bash
   sudo pacman -S gradle
   cd android && gradle wrapper --gradle-version 8.10.2
   ```
   Después **sube a git** `gradlew`, `gradlew.bat` y `gradle/wrapper/*`; el CI los necesita.
3. **Sync** Gradle y acepta las sugerencias de actualización de versiones solo si fallan las actuales.
4. Conecta el móvil por USB → `adb devices` debe listarlo → **Run**.
5. En el móvil: mantén pulsado el escritorio → Widgets → **4u** → prueba los 3 tamaños.

## 7. Primera prueba de punta a punta

1. Escritorio: registra la cuenta A.
2. Android: registra la cuenta B.
3. A → **Generar código** → B lo introduce → los dos se ven como contactos.
4. A envía una foto a B → B recibe la push y el widget se actualiza.
5. B envía 💭 a A → notificación en escritorio y el widget tiembla.
6. B reacciona ❤️ → A recibe aviso.
7. A programa una foto para dentro de 2 minutos → llega a su hora.

Si algo falla:
- **Panel → Edge Functions → notify → Logs**: errores de push.
- **Panel → Database → Logs**: errores de triggers y pg_net.
- `select * from net._http_response order by created desc limit 10;` → respuestas de las llamadas a funciones.

## 8. Publicar una versión

Una sola vez (secretos del CI, leídos de `desktop/.env`, `android/app/google-services.json` y `~/.secrets`):

```bash
scripts/ci-secrets.sh
```

Claves que viven fuera del repo, en `~/.secrets` (guárdalas también en otro sitio: sin ellas no se pueden firmar actualizaciones):
- `4u-release.jks` + `4u-release.pass`: firma del APK (alias `4u`). Si se pierde, los móviles tendrán que desinstalar para instalar la siguiente.
- `4u-updater.key` + `4u-updater.pass`: firma de las actualizaciones del escritorio (la clave pública está en `tauri.conf.json`).

Para compilar en local el release de Android, `android/local.properties` lleva `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD`.
`npm run tauri build` en local necesita `TAURI_SIGNING_PRIVATE_KEY` y `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` en el entorno.

Cada versión:

```bash
scripts/release.sh 0.3.0     # sube la versión, commit, tag v0.3.0 y push
gh run watch                 # el CI publica instaladores, APK y latest.json
```

En este PC (Arch) se instala con el paquete local:

```bash
cd packaging/arch
sed -i 's/^pkgver=.*/pkgver=0.3.0/' PKGBUILD
updpkgsums && makepkg -si
```

**Del APK de depuración al de release**: están firmados con claves distintas, así que la primera vez hay que desinstalar la de depuración (se pierde el historial local del móvil y hay que volver a iniciar sesión).
