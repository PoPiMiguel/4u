import type { HistoryItem } from "./types";

// Historial local en localStorage (compartido entre ventana principal y widget,
// mismo origen). TODO(M3): migrar a SQLite (tauri-plugin-sql) si crece.

const KEY = "4u.history";
const MAX = 300;

export function loadHistory(): HistoryItem[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? "[]") as HistoryItem[];
  } catch {
    return [];
  }
}

function save(items: HistoryItem[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(items.slice(0, MAX)));
  } catch (e) {
    console.error("No se pudo guardar el historial", e);
  }
}

export function hasItem(id: string): boolean {
  return loadHistory().some((i) => i.id === id);
}

/** Inserta en orden (lo más nuevo primero): al entrar se carga historial antiguo. */
export function addToHistory(item: HistoryItem) {
  const items = [item, ...loadHistory().filter((i) => i.id !== item.id)];
  items.sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  save(items);
}

// ---------- Mensaje fijado en el widget ----------

const PINNED = "4u.widgetPinned";

export function pinnedId(): string | null {
  return localStorage.getItem(PINNED);
}

export function setPinned(id: string | null) {
  if (id) localStorage.setItem(PINNED, id);
  else localStorage.removeItem(PINNED);
}

export function updateHistoryItem(id: string, patch: Partial<HistoryItem>) {
  save(loadHistory().map((i) => (i.id === id ? { ...i, ...patch } : i)));
}
