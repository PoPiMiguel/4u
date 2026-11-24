use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    AppHandle, LogicalSize, Manager, WindowEvent,
};

/// Descarga un archivo recibido (URL firmada de Supabase) y lo guarda:
/// - photo / gif → Imágenes/4u
/// - video       → Vídeos/4u
/// - resto       → <datos de la app>/media
#[tauri::command]
async fn download_media(app: AppHandle, url: String, file_name: String, kind: String) -> Result<String, String> {
    if !url.starts_with("https://") {
        return Err("URL no válida".into());
    }
    let safe: String = file_name
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_'))
        .collect();
    if safe.is_empty() || safe.starts_with('.') {
        return Err("Nombre de archivo no válido".into());
    }

    let paths = app.path();
    let dir = match kind.as_str() {
        "photo" | "gif" => paths.picture_dir().map(|d| d.join("4u")),
        "video" => paths.video_dir().map(|d| d.join("4u")),
        _ => paths.app_data_dir().map(|d| d.join("media")),
    }
    .map_err(|e| e.to_string())?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;

    let bytes = reqwest::get(&url)
        .await
        .map_err(|e| e.to_string())?
        .error_for_status()
        .map_err(|e| e.to_string())?
        .bytes()
        .await
        .map_err(|e| e.to_string())?;

    let path = dir.join(&safe);
    std::fs::write(&path, &bytes).map_err(|e| e.to_string())?;
    Ok(path.to_string_lossy().into_owned())
}

#[tauri::command]
fn set_widget_size(app: AppHandle, size: String) -> Result<(), String> {
    let (w, h) = match size.as_str() {
        "small" => (170.0, 170.0),
        "medium" => (350.0, 170.0),
        "large" => (350.0, 350.0),
        _ => return Err("Tamaño desconocido".into()),
    };
    let win = app.get_webview_window("widget").ok_or("No existe la ventana widget")?;
    win.set_size(LogicalSize::new(w, h)).map_err(|e| e.to_string())
}

#[tauri::command]
fn toggle_widget(app: AppHandle) -> Result<bool, String> {
    let win = app.get_webview_window("widget").ok_or("No existe la ventana widget")?;
    let visible = win.is_visible().map_err(|e| e.to_string())?;
    let result = if visible { win.hide() } else { win.show() };
    result.map_err(|e| e.to_string())?;
    Ok(!visible)
}

#[tauri::command]
fn show_main(app: AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        // Segunda instancia → enfoca la existente.
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            show_main(app.clone());
        }))
        .plugin(tauri_plugin_notification::init())
        .setup(|app| {
            let open = MenuItem::with_id(app, "open", "Abrir 4u", true, None::<&str>)?;
            let widget = MenuItem::with_id(app, "widget", "Mostrar/ocultar widget", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Salir", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &widget, &quit])?;

            TrayIconBuilder::new()
                .icon(app.default_window_icon().unwrap().clone())
                .tooltip("4u")
                .menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "open" => show_main(app.clone()),
                    "widget" => {
                        let _ = toggle_widget(app.clone());
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .build(app)?;
            Ok(())
        })
        // Cerrar la ventana principal la oculta: la app sigue en la bandeja recibiendo.
        .on_window_event(|window, event| {
            if window.label() == "main" {
                if let WindowEvent::CloseRequested { api, .. } = event {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            download_media,
            set_widget_size,
            toggle_widget,
            show_main
        ])
        .run(tauri::generate_context!())
        .expect("error al arrancar 4u");
}
