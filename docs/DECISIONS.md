# Decisiones técnicas (lagunas resueltas)

Registro de decisiones para no volver a discutirlas. Si alguna cambia, actualiza esta lista.

## 1. Login
**Email + contraseña, sin confirmación de correo.**
El SMTP integrado de Supabase solo permite unos pocos correos por hora en el plan gratis. Con la confirmación activada, registrarse fallaría a menudo.
Más adelante (M9, si se hace público): login con Google o SMTP propio gratuito (p. ej. Resend, con plan gratis).

## 2. Identificador de la app
**`app.fouru`** en Android y Tauri. En Kotlin/Java un paquete no puede empezar por número (`4u`). Para Play Store debe ser único; se puede cambiar antes de publicar, pero no después.

## 3. Contactos
**Código de 6 caracteres, válido 24 h y de un solo uso.** Quien lo introduce y quien lo generó quedan como contactos mutuos.
Sin caracteres ambiguos (0/O, 1/I). QR en el futuro: el QR simplemente contiene el código.
**Bloquear:** el estado `blocked` en tu fila corta el envío en ambos sentidos (`can_send` exige que las dos filas estén en `accepted`).

## 4. Flujo de envío
1. El cliente genera el UUID del mensaje.
2. Sube los archivos a `media/{su_uid}/{uuid}/original.ext` (+ `thumb.jpg`).
3. Llama a la RPC `send_message`, que valida que los destinatarios sean contactos y que la ruta sea suya.

Ventaja: no quedan mensajes "a medias" en la base de datos. Si la subida falla, no se crea el mensaje.
Pendiente: limpiar archivos subidos sin mensaje (subida OK y RPC fallida). Poco frecuente; lo resolverá `purge` en M7.

## 5. Envíos programados
`status = 'scheduled'` + `deliver_at`. **RLS oculta el mensaje y su archivo al destinatario** hasta que pg_cron (cada minuto) lo pasa a `sent`. Precisión: alrededor de un minuto.
Se consideran programados solo si `deliver_at` cae a más de 30 s vista; si no, se envían al momento.

## 6. Notificaciones
- **Android**: trigger de Postgres → `pg_net` → Edge Function `notify` → FCM HTTP v1 (mensaje de **datos**, prioridad alta). La app decide qué notificar y actualiza el widget.
- **Escritorio**: **Supabase Realtime** (`postgres_changes` sobre `messages` y `message_recipients`). Sin FCM: la app vive en la bandeja.
- Funciones protegidas con la cabecera `x-4u-secret`, porque no hay JWT de usuario. Las URL y secretos se guardan en Vault, no en el repo.

## 7. El servidor es un buzón temporal
Los archivos se borran de Storage cuando **todos** los destinatarios confirman la descarga (`downloaded_at`), o a los **30 días**. Lo hace `purge` (pg_cron diario). Los metadatos se quedan (`status = purged`).
El borrado va por Edge Function y no por SQL, porque Supabase no permite borrar `storage.objects` directamente desde SQL.

## 8. Límites de tamaño

| Tipo | Límite | Tratamiento |
|---|---|---|
| Foto | – | Se redimensiona a 2048 px, JPEG 85 % |
| GIF | 10 MB | Tal cual |
| Vídeo | 15 MB | Escritorio: rechaza si supera el límite (ffmpeg en M3). Android: Media3 Transformer (M3) |
| HTML | 2 MB | Un único archivo; assets en línea / data URI |
| Miniatura | 512 px | JPEG, la genera el emisor |

Se sube siempre como `application/octet-stream`; el tipo real va en `messages.mime`.

## 9. Animaciones HTML (seguridad)
- **Escritorio**: `<iframe sandbox="allow-scripts" srcdoc>` con origen opaco, así que no puede tocar la app ni la API de Tauri. Además se inyecta una CSP que solo permite contenido en línea y `data:`/`blob:` (**sin red**).
  La CSP de la app Tauri es `null` porque un `srcdoc` hereda la CSP del padre, y una CSP estricta rompería las animaciones. La protección real la dan el sandbox del iframe y la CSP inyectada.
- **Android** (M3): WebView a pantalla completa con JS activado, sin `addJavascriptInterface`, sin acceso a archivos y con las peticiones de red bloqueadas en `shouldInterceptRequest`.
- El widget de Android no ejecuta HTML. En M3, el receptor renderiza el HTML en un WebView fuera de pantalla, captura fotogramas y los rota con `ViewFlipper` (vía `AndroidRemoteViews` dentro de Glance).

## 10. Historial local
- **Escritorio**: `localStorage` (compartido entre la ventana principal y el widget). Archivos en `~/Imágenes/4u`, `~/Vídeos/4u` y en los datos de la app (HTML).
- **Android**: `filesDir/history.json`. Fotos y vídeos en la galería (`Pictures/4u`, `Movies/4u`); HTML y dibujos en almacenamiento privado; miniaturas del widget en `filesDir/thumbs`.
- Si crece, migrar a SQLite (escritorio) o Room (Android).

## 11. Widget de escritorio
Una segunda ventana Tauri (`widget`): sin bordes, transparente, sin barra de tareas y `alwaysOnBottom`. Tamaños S/M/L: 170², 350×170 y 350².
Solo la ventana principal sincroniza; el widget recibe el evento `inbox:new` y lee `localStorage`.
En **Hyprland** se ignora `alwaysOnBottom`: hay que usar reglas de ventana (fase 1) o layer-shell (fase 2).

## 12. Dibujo (M5)
Se envían los trazos como JSON (`storage_path`, para reproducir el dibujo "dibujándose") más un PNG final como `thumb`. Formato:
```json
{ "w": 1, "h": 1.5, "bg": "#000000",
  "strokes": [ { "color": "#e84a6f", "width": 0.01, "points": [[x, y, tMs], ...] } ] }
```
Coordenadas normalizadas entre 0 y 1 para que se vea igual en cualquier pantalla: `x` sobre el ancho e `y` sobre el alto; `w`/`h` solo dan la proporción (2:3, vertical). `width` es el grosor relativo al ancho.
`t` son milisegundos desde el principio, pero las pausas entre trazos se recortan a 350 ms, y la reproducción se acelera para durar 6 s como mucho.
El PNG final va como JPEG (`thumb.jpg`), igual que el resto de miniaturas. Implementación: `desktop/src/lib/drawing.ts` y `android/.../data/Drawing.kt`.

## 13. Versiones Android
`minSdk 29` (Android 10): MediaStore sin permisos de almacenamiento. `targetSdk 35`.
Las versiones de las librerías son de finales de 2024; Android Studio propondrá subirlas.

## 14. Actualizaciones (M7)
El repo es **público** para que las releases se puedan descargar sin sesión (los secretos nunca han estado en git).
Una única versión para todo: la de `desktop/src-tauri/tauri.conf.json`; Android la lee al compilar (`versionCode = major·10000 + minor·100 + patch`). `scripts/release.sh X.Y.Z` la sube, hace tag y lanza el CI.
- **Escritorio**: plugin updater de Tauri + `latest.json` firmado en GitHub Releases (`releases/latest/download/latest.json`, por eso las releases no son borradores).
  En Linux solo se actualiza sola la AppImage; instalado con pacman (`packaging/arch/PKGBUILD`, `4u-bin`, solo local) se actualiza con el PKGBUILD.
- **Android**: la app consulta `releases/latest` en la API de GitHub y ofrece descargar el APK.
  Hace falta **un keystore de release fijo**: los APK de depuración firmados en máquinas distintas no se pueden actualizar uno encima de otro.

## 15. Notificaciones con la app cerrada (Android)
La push de FCM es solo de datos. Al llegar, `PushService` **muestra la notificación al momento** con lo que trae (remitente, tipo y texto) y después encola la descarga.
Si se esperase a la descarga, Android (y más Samsung) puede retrasar el trabajo con la app cerrada, y FCM rebaja la prioridad de las push de alta prioridad que no muestran nada.
El worker vuelve a publicar la misma notificación (mismo id) con `setOnlyAlertOnce`, así que no suena dos veces.
En Cuenta hay un botón para quitar la optimización de batería. Después de un "Forzar detención", Android no entrega push hasta abrir la app una vez.

## 16. Contactos y avatares
- **Eliminar** borra la relación en los dos sentidos (`remove_contact`); para volver a hablar hace falta otro código. **Bloquear** solo cambia tu fila.
- Los perfiles (nombre y avatar) de un contacto bloqueado siguen siendo visibles para quien lo tiene en la lista (`private.has_contact_row`).
- Avatares en el bucket privado `avatars`, ruta `{uid}/{uuid}.jpg` (256 px). Cada cambio crea un nombre nuevo (las cachés no se quedan con la vieja) y borra el anterior.
- **Enviados** (`my_sent`): como `purge` borra las miniaturas en cuanto todos descargan, el emisor guarda en local una miniatura pequeña de lo que envía.
