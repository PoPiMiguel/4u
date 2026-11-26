import { supabase } from "./supabase";
import type { Contact, MessageType } from "./types";

function check<T>(res: { data: T; error: unknown }): T {
  if (res.error) throw res.error;
  return res.data;
}

export async function createInvite(): Promise<string> {
  return check(await supabase.rpc("create_invite")) as string;
}

export async function acceptInvite(code: string): Promise<void> {
  check(await supabase.rpc("accept_invite", { p_code: code }));
}

export async function listContacts(userId: string): Promise<Contact[]> {
  const rows = check(
    await supabase
      .from("contacts")
      .select("contact_id, status, nickname, contact:profiles!contacts_contact_id_fkey(display_name)")
      .eq("user_id", userId),
  ) as unknown as {
    contact_id: string;
    status: "accepted" | "blocked";
    nickname: string | null;
    contact: { display_name: string } | null;
  }[];

  return rows.map((r) => ({
    contactId: r.contact_id,
    status: r.status,
    nickname: r.nickname,
    name: r.nickname ?? r.contact?.display_name ?? "?",
  }));
}

export interface SendOptions {
  type: MessageType;
  recipients: string[];
  file?: Blob;
  ext?: string;
  mime?: string;
  thumb?: Blob;
  caption?: string | null;
  deliverAt?: Date | null;
}

/** Sube los archivos a media/{uid}/{id}/ y registra el mensaje con la RPC send_message. */
export async function sendMessage(userId: string, opts: SendOptions): Promise<string> {
  const id = crypto.randomUUID();
  const bucket = supabase.storage.from("media");
  let storagePath: string | null = null;
  let thumbPath: string | null = null;

  // Siempre octet-stream: el receptor descarga bytes; el tipo real va en `mime`.
  if (opts.file) {
    storagePath = `${userId}/${id}/original.${opts.ext ?? "bin"}`;
    check(await bucket.upload(storagePath, opts.file, { contentType: "application/octet-stream" }));
  }
  if (opts.thumb) {
    thumbPath = `${userId}/${id}/thumb.jpg`;
    check(await bucket.upload(thumbPath, opts.thumb, { contentType: "application/octet-stream" }));
  }

  check(
    await supabase.rpc("send_message", {
      p_id: id,
      p_type: opts.type,
      p_recipients: opts.recipients,
      p_storage_path: storagePath,
      p_thumb_path: thumbPath,
      p_mime: opts.mime ?? null,
      p_size: opts.file?.size ?? null,
      p_caption: opts.caption ?? null,
      p_deliver_at: opts.deliverAt ? opts.deliverAt.toISOString() : null,
    }),
  );
  return id;
}

export interface PendingMessage {
  id: string;
  type: MessageType;
  storage_path: string | null;
  caption: string | null;
  sender_id: string;
  created_at: string;
  sender: { display_name: string } | null;
}

/** Mensajes recibidos aún no descargados en este usuario. */
export async function fetchPending(userId: string): Promise<PendingMessage[]> {
  const rows = check(
    await supabase
      .from("message_recipients")
      .select(
        "message_id, message:messages!message_recipients_message_id_fkey(id, type, storage_path, caption, sender_id, created_at, sender:profiles!messages_sender_id_fkey(display_name))",
      )
      .eq("recipient_id", userId)
      .is("downloaded_at", null),
  ) as unknown as { message_id: string; message: PendingMessage | null }[];

  return rows
    .map((r) => r.message)
    .filter((m): m is PendingMessage => !!m)
    .sort((a, b) => a.created_at.localeCompare(b.created_at));
}

async function updateReceipt(messageId: string, userId: string, patch: Record<string, string | null>) {
  check(
    await supabase
      .from("message_recipients")
      .update(patch)
      .eq("message_id", messageId)
      .eq("recipient_id", userId),
  );
}

export function markDownloaded(messageId: string, userId: string) {
  const now = new Date().toISOString();
  return updateReceipt(messageId, userId, { delivered_at: now, downloaded_at: now });
}

export function markSeen(messageId: string, userId: string) {
  return updateReceipt(messageId, userId, { seen_at: new Date().toISOString() });
}

export function react(messageId: string, userId: string, emoji: string | null) {
  return updateReceipt(messageId, userId, { reaction: emoji });
}

// ---------- Envíos en espera ----------

export interface OutRecipient {
  id: string;
  name: string;
  delivered: boolean;
}

/** Envío propio programado o que algún destinatario aún no ha recibido. */
export interface OutPending {
  id: string;
  type: MessageType;
  caption: string | null;
  storagePath: string | null;
  thumbPath: string | null;
  status: "scheduled" | "sent";
  deliverAt: string;
  createdAt: string;
  recipients: OutRecipient[];
  /** URL firmada de la miniatura (si tiene). */
  thumbUrl: string | null;
}

export async function listOutPending(): Promise<OutPending[]> {
  const rows = check(await supabase.rpc("my_pending")) as {
    id: string;
    type: MessageType;
    caption: string | null;
    storage_path: string | null;
    thumb_path: string | null;
    status: "scheduled" | "sent";
    deliver_at: string;
    created_at: string;
    recipients: OutRecipient[];
  }[];

  const thumbs = rows.map((r) => r.thumb_path).filter((p): p is string => !!p);
  const urls = new Map<string, string>();
  if (thumbs.length > 0) {
    const { data } = await supabase.storage.from("media").createSignedUrls(thumbs, 3600);
    for (const d of data ?? []) if (d.path && d.signedUrl) urls.set(d.path, d.signedUrl);
  }

  return rows.map((r) => ({
    id: r.id,
    type: r.type,
    caption: r.caption,
    storagePath: r.storage_path,
    thumbPath: r.thumb_path,
    status: r.status,
    deliverAt: r.deliver_at,
    createdAt: r.created_at,
    recipients: r.recipients,
    thumbUrl: r.thumb_path ? urls.get(r.thumb_path) ?? null : null,
  }));
}

/** Cancela un envío en espera. Si se borra entero, borra también sus archivos. */
export async function cancelOutPending(item: OutPending): Promise<void> {
  const deleted = check(await supabase.rpc("cancel_pending", { p_id: item.id })) as boolean;
  const paths = [item.storagePath, item.thumbPath].filter((p): p is string => !!p);
  if (deleted && paths.length > 0) {
    const { error } = await supabase.storage.from("media").remove(paths);
    if (error) console.error("No se pudieron borrar los archivos", error);
  }
}
