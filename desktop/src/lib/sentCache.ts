// Miniaturas pequeñas de lo que envío, guardadas en local: `purge` borra las
// del servidor en cuanto todos han descargado el envío.

const KEY = "4u.sentThumbs";
const MAX = 100;

type Cache = [string, string][];

function load(): Cache {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? "[]") as Cache;
  } catch {
    return [];
  }
}

export function rememberSentThumb(id: string, dataUrl: string) {
  try {
    const next = [[id, dataUrl], ...load().filter(([k]) => k !== id)].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch (e) {
    console.error("No se pudo guardar la miniatura enviada", e);
  }
}

export function sentThumbs(): Map<string, string> {
  return new Map(load());
}
