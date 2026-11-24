# 4u — Plan del proyecto

App tipo "widget social": envías fotos, vídeos, GIFs, dibujos o animaciones HTML a tus contactos y les aparecen en un widget de su móvil o escritorio.

## Plataformas

| Plataforma | Estado |
|---|---|
| Android | Objetivo principal móvil |
| Windows | Objetivo principal escritorio |
| Linux (Arch + Hyprland) | Escritorio, mismo código que Windows |
| iOS / macOS | Fuera de alcance por ahora |

## Stack

| Parte | Tecnología |
|---|---|
| Escritorio (Windows + Linux) | Tauri 2 (Rust) + React + TypeScript + Vite |
| Android | Kotlin, Jetpack Compose, Glance (widget), Media3 (vídeo), WorkManager |
| Backend | Supabase (plan gratuito): Auth, Postgres, Storage, Realtime, Edge Functions, pg_cron |
| Push Android | Firebase Cloud Messaging (plan Spark gratuito, solo FCM) |
| CI / releases | GitHub Actions (`tauri-action` para escritorio, Gradle para APK) |

### Coste: 0 €

Requisito: ningún servicio con tarjeta de crédito.

- **Supabase Free**: 500 MB de base de datos, 1 GB de Storage, ~5 GB/mes de transferencia, 50 MB por archivo. No pide tarjeta. Un proyecto se pausa tras 7 días sin actividad (con uso diario no ocurre; se reactiva desde el panel).
- **FCM**: gratis e ilimitado en plan Spark. Firebase se usa solo para las notificaciones push.
- **GitHub Actions**: gratis en repos públicos, 2000 min/mes en privados.

### El servidor como relevo, no como almacén

El backend solo hace de buzón temporal, porque los dispositivos no siempre están conectados a la vez:

1. El emisor sube el archivo a Storage.
2. El receptor recibe la push, lo descarga y lo guarda en local (galería / carpeta de la app).
3. Cuando todos los destinatarios confirman la descarga, el archivo se borra del servidor. Límite de seguridad: 30 días.

El historial vive en cada dispositivo. En el servidor solo quedan metadatos ligeros (quién, cuándo, tipo, reacción). Así 1 GB sobra.

Para no gastar la transferencia mensual:
- Vídeo comprimido a 720p, máximo ~30 s / 15 MB.
- Fotos redimensionadas a 2048 px en webp.
- HTML máximo 2 MB.

Plan B si se queda corto: PocketBase autoalojado (un único binario) en una Raspberry Pi o un PC siempre encendido + Cloudflare Tunnel (gratis).

## Estructura del repo

```
4u/
├── android/            App Android (Kotlin)
├── desktop/            App escritorio (Tauri)
│   ├── src/            Frontend React/TS
│   └── src-tauri/      Backend Rust (ventanas widget, bandeja, layer-shell)
├── supabase/
│   ├── migrations/     Tablas, políticas RLS, pg_cron
│   └── functions/      Edge Functions: push FCM, envíos programados, limpieza
├── docs/
└── .github/workflows/
```

## Funcionalidades

### Tipos de contenido
- **Foto** (jpg/png/webp/heic → se sube webp/jpg)
- **Vídeo** (comprimido antes de subir, límite de tamaño/duración)
- **GIF**
- **Animación HTML**: un único `.html` autocontenido (CSS/JS inline, assets como data URI). Tamaño máximo inicial: 2 MB. Las animaciones las crea el usuario fuera de la app; no hay editor ni plantillas.
- **Dibujo**: lienzo integrado. Se envían los trazos (JSON) para que el receptor vea el dibujo "dibujándose", más un PNG final para el widget.
- **Toque / "pienso en ti"**: sin contenido, vibración + animación en el widget.

### Social
- Varios contactos. Emparejamiento por código de invitación o QR.
- Enviar a uno o varios contactos a la vez.
- Reacciones (emoji) y confirmación de entregado / visto.
- Historial por contacto (timeline).
- Envíos programados: elegir fecha/hora y contenido (foto, vídeo, HTML, dibujo…).

### Widget
Tres tamaños en ambas plataformas:

| Tamaño | Contenido |
|---|---|
| Pequeño | Último elemento recibido |
| Mediano | Último elemento + remitente + hora + reacción rápida |
| Grande | Último elemento + tira con los 3 anteriores + botón de toque |

Cada widget se puede configurar para un contacto concreto o "todos".

**Android**: los widgets no pueden ejecutar HTML ni GIF. Muestran una vista previa; al tocar se abre la app y reproduce a pantalla completa. Para dar sensación de movimiento, el dispositivo receptor renderiza el HTML/GIF en un WebView fuera de pantalla, captura N fotogramas y el widget los rota (ViewFlipper).

**Escritorio**: ventana sin bordes que reproduce todo el contenido de forma nativa.
- Windows: ventana sin bordes, sin barra de tareas, anclada al escritorio.
- Linux / Hyprland, fase 1: ventana normal + reglas `windowrulev2` (float, pin, sin bordes, posición).
- Linux / Hyprland, fase 2: `gtk-layer-shell` en capa background (widget real de escritorio) + módulo custom de Waybar (`4u --waybar` emite JSON con no leídos; clic = abrir / enviar toque).

### Notificaciones
- Android: FCM (mensajes de datos de alta prioridad) → actualiza widget + notificación.
- Escritorio: la app vive en la bandeja, escucha Supabase Realtime y lanza notificación nativa.

### Almacenamiento local
- Android: fotos/vídeos → galería (MediaStore, álbum "4u"). HTML/dibujos → almacenamiento privado de la app.
- Escritorio: `Imágenes/4u` y `Vídeos/4u`; HTML/dibujos en el directorio de datos de la app.
- Caché para mostrar el último contenido sin conexión. Envíos sin conexión quedan en cola.

### Actualizaciones
- Android: APK en GitHub Releases + comprobación en la app (API de GitHub). Play Store más adelante.
- Escritorio: updater de Tauri con `latest.json` firmado en GitHub Releases. Windows: `.msi`/`.exe`. Linux: `.AppImage` y `.deb`; paquete AUR (`4u-bin`) más adelante.

## Seguridad

El contenido HTML es código de terceros. Se ejecuta siempre aislado:
- **Escritorio**: `<iframe sandbox="allow-scripts">` con CSP `default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; media-src data: blob:; font-src data:`. Sin acceso a la API de Tauri.
- **Android**: WebView con JS activado, sin `addJavascriptInterface`, sin acceso a ficheros, peticiones de red bloqueadas en `shouldInterceptRequest`.
- Políticas RLS (Postgres y Storage): solo emisor y destinatarios leen un mensaje; solo contactos aceptados pueden enviarse cosas.
- Para publicación abierta: bloquear/reportar usuarios (exigido por Google Play para contenido generado por usuarios). Cifrado extremo a extremo como mejora futura.

## Modelo de datos (Postgres en Supabase)

```
profiles        id (= auth.uid), display_name, avatar_path, created_at
devices         id, user_id, platform (android|windows|linux), fcm_token, last_seen
contacts        user_id, contact_id, status (pending|accepted|blocked), nickname, since
invites         code, owner_id, expires_at

messages        id, sender_id, type (photo|video|gif|html|drawing|ping),
                storage_path, thumb_path, mime, size, caption,
                created_at, deliver_at, status (scheduled|sent|purged)
message_recipients
                message_id, recipient_id, delivered_at, downloaded_at,
                seen_at, reaction
```

Storage (bucket privado `media`): `{sender_id}/{message_id}/{original.*, thumb.webp}`

Lógica de servidor:
- **Trigger + Edge Function `notify`**: al crear un mensaje no programado → push FCM a los dispositivos Android de los destinatarios. El escritorio se entera vía Realtime.
- **pg_cron cada minuto → `dispatch_scheduled`**: mensajes `scheduled` con `deliver_at <= now()` pasan a `sent` y disparan `notify`.
- **Trigger en `message_recipients`**: reacción o visto → push al emisor.
- **pg_cron diario → `purge`**: borra de Storage los archivos ya descargados por todos o con más de 30 días.

## Hitos

| # | Hito | Contenido |
|---|---|---|
| M0 | Base | Herramientas, proyecto Supabase + Firebase (solo FCM), monorepo, CI vacío |
| M1 | Cuentas y contactos | Login, código de invitación/QR, lista de contactos |
| M2 | Primer envío | Foto → push → widget (Android 3 tamaños + escritorio) + historial |
| M3 | Más contenido | Vídeo, GIF, HTML aislado, fotogramas para widget Android, guardado en galería/carpeta |
| M4 | Interacción | Reacciones, entregado/visto, toque |
| M5 | Lienzo | Dibujo con reproducción de trazos |
| M6 | Programados | Envíos con fecha/hora |
| M7 | Distribución | Releases firmadas, updater, AppImage/deb/msi/APK |
| M8 | Hyprland | layer-shell + módulo Waybar |
| M9 | Público | Bloquear/reportar, Play Store, AUR, E2E |

## Requisitos de desarrollo (Windows)

- Node.js ✅
- Rust (`rustup`) + Visual Studio Build Tools (C++)
- Android Studio (SDK + JDK 17)
- Supabase CLI (`npx supabase`)
- WebView2 (incluido en Windows 11)
