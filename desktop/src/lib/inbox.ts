import { invoke } from "@tauri-apps/api/core";
import { emit } from "@tauri-apps/api/event";
import { supabase } from "./supabase";
import { fetchPending, markDownloaded, type PendingMessage } from "./api";
import { addToHistory, hasItem } from "./store";
import { notify } from "./notify";
import type { HistoryItem } from "./types";

// Solo la ventana principal sincroniza. El widget se entera por el evento "inbox:new".

let running = false;
let again = false;

export async function syncInbox(userId: string): Promise<void> {
  if (running) {
    again = true;
    return;
  }
  running = true;
  try {
    do {
      again = false;
      for (const m of await fetchPending(userId)) {
        try {
          await processOne(userId, m);
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
  ping: "Está pensando en ti 💭",
  photo: "📷 Foto nueva",
  video: "🎬 Vídeo nuevo",
  gif: "✨ GIF nuevo",
  html: "🌸 Animación nueva",
  drawing: "🎨 Dibujo nuevo",
};

async function processOne(userId: string, m: PendingMessage) {
  // Ya descargado antes pero sin confirmar (fallo de red): solo confirmar.
  if (hasItem(m.id)) {
    await markDownloaded(m.id, userId);
    return;
  }

  let localPath: string | null = null;
  if (m.storage_path) {
    const { data, error } = await supabase.storage.from("media").createSignedUrl(m.storage_path, 300);
    if (error) throw error;
    const ext = m.storage_path.split(".").pop() ?? "bin";
    localPath = await invoke<string>("download_media", {
      url: data.signedUrl,
      fileName: `4u_${m.id}.${ext}`,
      kind: m.type,
    });
  }

  const item: HistoryItem = {
    id: m.id,
    type: m.type,
    senderId: m.sender_id,
    senderName: m.sender?.display_name ?? "Alguien",
    caption: m.caption,
    localPath,
    receivedAt: new Date().toISOString(),
    seen: false,
    reaction: null,
  };
  addToHistory(item);
  await markDownloaded(m.id, userId);
  await emit("inbox:new", item);
  await notify(item.senderName, item.caption || BODY[item.type] || "Nuevo mensaje");
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
      else if (now.seen_at && !old.seen_at) notify("4u", "Han visto tu mensaje 👀");
    })
    .subscribe();

  return () => {
    supabase.removeChannel(channel);
  };
}
