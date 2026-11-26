# TODO técnico por hitos

Estado: ✅ hecho · 🟡 escrito sin probar · ⬜ pendiente

## M0 — Base
- ✅ Plan, decisiones y guía de instalación
- ✅ Migración SQL completa (`supabase/migrations/…_init.sql`): aplicada en el proyecto real
- ✅ Edge Functions `notify` (FCM v1) y `purge`: desplegadas; `purge` probada desde la BD (pg_net → 200)
- 🟡 Esqueleto escritorio (Tauri 2 + React)
- 🟡 Esqueleto Android (Compose + Glance + FCM + WorkManager): compila, falta probarlo en el móvil
- 🟡 CI de releases (`.github/workflows/release.yml`)
- ✅ Cuentas Supabase y Firebase → [TAREAS.md](TAREAS.md)
- ✅ `npx supabase db push --remote` sin errores (CLI 3.x: hace falta `--remote`)
- ⬜ `npm run tauri dev` compila
- ✅ Android: Gradle wrapper 8.10.2 subido; `./gradlew assembleDebug` compila con JDK 17
- ⬜ Icono Android adaptativo (Image Asset Studio con `assets/logo.svg`)

**Posibles fallos en el primer build** (código escrito sin compilar):
- supabase-kt: si no existe `auth.awaitInitialization()` o `SessionStatus.Initializing`, revisa los nombres en la versión instalada.
  En la 2.x eran `SessionStatus.LoadingFromStorage` y no hay `awaitInitialization`.
- Glance: imports de `defaultWeight`/`cornerRadius` según la versión. (Hecho: `actionStartActivity<T>()` va en `androidx.glance.action`.)
- Tauri: si se queja del feature `protocol-asset` o de las capabilities, el mensaje de error dice exactamente qué añadir.

## M1 — Cuentas y contactos
- 🟡 Registro / login (escritorio y Android)
- 🟡 Código de invitación y aceptar
- ⬜ QR del código (escritorio: mostrar; Android: escanear con CameraX/ML Kit o pegar)
- ⬜ Renombrar contacto (`nickname`), bloquear / desbloquear, eliminar
- ⬜ Avatar (bucket `avatars` + política)

## M2 — Primer envío
- 🟡 Foto escritorio → Android (push + widget)
- 🟡 Foto Android → escritorio (Realtime + notificación + widget)
- 🟡 Widget Android con 3 tamaños
- 🟡 Widget escritorio con 3 tamaños
- 🟡 Historial básico
- ⬜ Configurar el widget de Android por contacto (actividad de configuración + `GlanceAppWidgetManager`)
- ⬜ Arranque automático del escritorio (`tauri-plugin-autostart`)
- ⬜ Recordar la posición del widget de escritorio (`tauri-plugin-window-state`)

## M3 — Más contenido
- 🟡 Escritorio: enviar GIF, HTML y vídeo ≤ 15 MB
- ⬜ Escritorio: comprimir vídeo con ffmpeg (en Linux, el del sistema; en Windows, sidecar)
- ⬜ Android: enviar vídeo (Media3 Transformer → 720p) y HTML (selector de documentos)
- ⬜ Android: reproductor a pantalla completa (WebView aislado para HTML, ExoPlayer para vídeo)
- ⬜ Android: fotogramas del HTML/GIF para el widget (WebView offscreen → N bitmaps → ViewFlipper)
- ⬜ Historial en SQLite / Room

## M4 — Interacción
- 🟡 Reacciones y "visto" (escritorio y Android)
- 🟡 Toque 💭 (vibración en Android, temblor del widget en escritorio)
- ⬜ Mostrar al emisor el estado de lo que envió (entregado / visto / reacción) en una vista "Enviados"

## M5 — Lienzo
- ⬜ Componente de dibujo (escritorio: canvas; Android: Compose Canvas)
- ⬜ Formato de trazos ([DECISIONS.md §12](DECISIONS.md#12-dibujo-m5)) + reproducción animada

## M6 — Programados
- 🟡 Backend (pg_cron + RLS)
- 🟡 Escritorio: selector de fecha y hora
- ⬜ Android: selector de fecha y hora
- ⬜ Lista de programados con opción de cancelar (`cancel_scheduled` + borrar archivos)

## M7 — Distribución
- ⬜ Keystore de release Android + `signingConfig` + secretos en GitHub
- ⬜ Updater Tauri (`tauri-plugin-updater`, claves con `npx tauri signer generate`)
- ⬜ Android: comprobar actualizaciones en GitHub Releases
- ⬜ Limpiar subidas huérfanas en `purge`
- ⬜ PKGBUILD `4u-bin` para AUR

## M8 — Hyprland
- ⬜ Widget en la capa `background` con `gtk-layer-shell` (crate `gtk-layer-shell`, sobre el `gtk::ApplicationWindow` de Tauri, solo en Linux)
- ⬜ Módulo Waybar: `fouru --waybar` imprime JSON `{"text","tooltip","class"}` con los no vistos
  ```jsonc
  // ~/.config/waybar/config
  "custom/4u": {
    "exec": "fouru --waybar",
    "return-type": "json",
    "interval": 10,
    "on-click": "fouru --show",
    "on-click-right": "fouru --ping-last"
  }
  ```

## M9 — Público
- ⬜ Reportar / bloquear (lo exige Google Play para contenido generado por usuarios)
- ⬜ Política de privacidad
- ⬜ Login con Google, SMTP propio
- ⬜ Cifrado de extremo a extremo (libsodium: clave pública por dispositivo)
- ⬜ Play Store (25 $ una vez; las cuentas personales nuevas necesitan una prueba cerrada con 12 testers durante 14 días)
