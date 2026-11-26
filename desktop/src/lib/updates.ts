import { invoke } from "@tauri-apps/api/core";
import { check, type Update } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";

/**
 * Busca una versión nueva en GitHub Releases (latest.json firmado).
 * En Linux solo se actualiza sola la AppImage; si se instaló con pacman
 * (PKGBUILD 4u-bin) o .deb, las actualizaciones van por el gestor de paquetes.
 */
export async function findUpdate(): Promise<Update | null> {
  if (!(await invoke<boolean>("updater_supported"))) return null;
  return check();
}

export async function installUpdate(update: Update): Promise<void> {
  await update.downloadAndInstall();
  await relaunch();
}
