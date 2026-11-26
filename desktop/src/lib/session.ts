import { emit } from "@tauri-apps/api/event";
import { supabase } from "./supabase";
import { deviceId, resetDeviceRegistration } from "./device";

/**
 * Cierra sesión dejando el dispositivo limpio: borra su fila en `devices`
 * (y con ella lo que el servidor sabe que ya descargó) y el historial local.
 * Así, al volver a entrar (con esta u otra cuenta) se carga todo de nuevo.
 */
export async function signOutClean() {
  try {
    await supabase.from("devices").delete().eq("id", deviceId());
  } catch (e) {
    console.error("No se pudo dar de baja el dispositivo", e);
  }
  for (const k of ["4u.history", "4u.widgetPinned", "4u.sentThumbs"]) localStorage.removeItem(k);
  resetDeviceRegistration();
  await emit("widget:pin").catch(() => undefined);
  await supabase.auth.signOut();
}
