import { invoke } from "@tauri-apps/api/core";
import { emit } from "@tauri-apps/api/event";
import { supabase } from "./supabase";
import { deviceInbox, markDeviceDelivered, type InboxMessage } from "./api";
import { deviceId, registerDevice } from "./device";
import { addToHistory, hasItem } from "./store";
import { notify } from "./notify";
import type { HistoryItem } from "./types";

// Solo la ventana principal sincroniza. El widget se entera por el evento "inbox:new".
// Cada dispositivo pide lo que le falta (device_inbox): así el PC y el móvil de
// la misma cuenta reciben lo mismo, y al entrar en uno nuevo se carga el historial.

let running = false;
let again = false;

export async function syncInbox(_userId: string): Promise<void> {
  if (running) {
    again = true;
    return;
  }
  running = true;
  try {
    await registerDevice();
    do {
      again = false;
      for (const m of await deviceInbox(deviceId())) {
        try {
          await processOne(m);
        } catch (e) {
          console.error("Error procesando", m.id, e);
        }
      }
    } while (again);
  } finally {
    running = false;
  }
}

const BODY: Record<string, string> = {
  ping: "Está pensando en ti",
  photo: "Foto nueva",
  video: "Vídeo nuevo",
  gif: "GIF nuevo",
  html: "Animación nueva",
  drawing: "Dibujo nuevo",
};

/** Solo se avisa de lo reciente y sin ver (no del historial que se carga al entrar). */
const NOTIFY_WINDOW_MS = 60 * 60 * 1000;

async function download(path: string, fileName: string, kind: string): Promise<string> {
  const { data, error } = await supabase.storage.from("media").createSignedUrl(path, 300);
  if (error) throw error;
  return invoke<string>("download_media", { url: data.signedUrl, fileName, kind });
}

async function processOne(m: InboxMessage) {
  // Ya lo tengo en local (p. ej. falló la confirmación): solo confirmar.
  if (hasItem(m.id)) {
    await markDeviceDelivered(deviceId(), m.id);
    return;
  }

  let localPath: string | null = null;
  let thumbPath: string | null = null;
  if (m.storage_path) {
    const ext = m.storage_path.split(".").pop() ?? "bin";
    localPath = await download(m.storage_path, `4u_${m.id}.${ext}`, m.type);
  } else if (m.thumb_path) {
    // El original ya caducó (30 días): queda la miniatura.
    thumbPath = await download(m.thumb_path, `4u_${m.id}_thumb.jpg`, "thumb");
  }

  const item: HistoryItem = {
    id: m.id,
    type: m.type,
    senderId: m.sender_id,
    senderName: m.sender_name ?? "Alguien",
    caption: m.caption,
    localPath,
    thumbPath,
    expired: !m.storage_path && m.type !== "ping",
    receivedAt: m.sent_at,
    seen: m.seen,
    reaction: m.reaction,
  };
  addToHistory(item);
  await markDeviceDelivered(deviceId(), m.id);
  await emit("inbox:new", item);
  if (!m.seen && Date.now() - new Date(m.sent_at).getTime() < NOTIFY_WINDOW_MS) {
    await notify(item.senderName, item.caption || BODY[item.type] || "Mensaje nuevo");
  }
}

/** Escucha cambios en tiempo real. Devuelve la función para cancelar. */
export function subscribeInbox(userId: string, onChange: () => void): () => void {
  const channel = supabase
    .channel(`inbox-${userId}`)
    // RLS solo entrega mensajes visibles para mí (programados ocultos hasta su hora).
    .on("postgres_changes", { event: "*", schema: "public", table: "messages" }, (payload) => {
      const row = payload.new as { sender_id?: string; status?: string } | undefined;
      if (row && row.sender_id !== userId && row.status === "sent") {
        syncInbox(userId).then(onChange).catch(console.error);
      }
    })
    // Reacciones / visto de lo que yo envié.
    .on("postgres_changes", { event: "UPDATE", schema: "public", table: "message_recipients" }, (payload) => {
      const now = payload.new as { recipient_id: string; reaction: string | null; seen_at: string | null };
      const old = payload.old as { reaction?: string | null; seen_at?: string | null };
      if (now.recipient_id === userId) return;
      if (now.reaction && now.reaction !== old.reaction) notify("4u", `Han reaccionado ${now.reaction}`);
      else if (now.seen_at && !old.seen_at) notify("4u", "Han visto tu mensaje");
    })
    .subscribe();

  return () => {
    supabase.removeChannel(channel);
  };
}
