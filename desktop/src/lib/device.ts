import { invoke } from "@tauri-apps/api/core";
import { supabase } from "./supabase";

// Cada instalación es un dispositivo (tabla devices): el servidor lleva por
// dispositivo lo que cada uno ya ha descargado, así el PC y el móvil de la
// misma cuenta reciben lo mismo.

const KEY = "4u.deviceId";

export function deviceId(): string {
  let id = localStorage.getItem(KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(KEY, id);
  }
  return id;
}

let registered: Promise<void> | null = null;

/** Registra (o actualiza) este dispositivo. Una vez por sesión. */
export function registerDevice(): Promise<void> {
  registered ??= (async () => {
    const platform = await invoke<string>("platform");
    const { error } = await supabase.rpc("register_device", {
      p_id: deviceId(),
      p_platform: platform,
      p_name: `4u ${platform}`,
      p_fcm_token: null,
    });
    if (error) {
      registered = null;
      throw error;
    }
  })();
  return registered;
}

export function resetDeviceRegistration() {
  registered = null;
}
