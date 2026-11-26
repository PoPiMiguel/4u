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
      .select("contact_id, status, nickname, contact:profiles!contacts_contact_id_fkey(display_name, avatar_path)")
      .eq("user_id", userId),
  ) as unknown as {
    contact_id: string;
    status: "accepted" | "blocked";
    nickname: string | null;
    contact: { display_name: string; avatar_path: string | null } | null;
  }[];

  const urls = await signAvatars(rows.map((r) => r.contact?.avatar_path ?? null));
  return rows
    .map((r) => ({
      contactId: r.contact_id,
      status: r.status,
      nickname: r.nickname,
      displayName: r.contact?.display_name ?? "?",
      name: r.nickname ?? r.contact?.display_name ?? "?",
      avatarUrl: r.contact?.avatar_path ? urls.get(r.contact.avatar_path) ?? null : null,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** Renombra un contacto solo para mí (vacío = volver a su nombre). */
export async function renameContact(userId: string, contactId: string, nickname: string | null) {
  check(
    await supabase.from("contacts").update({ nickname }).eq("user_id", userId).eq("contact_id", contactId),
  );
}

/** Bloquear corta el envío en los dos sentidos (ver DECISIONS §3). */
export async function setBlocked(userId: string, contactId: string, blocked: boolean) {
  check(
    await supabase
      .from("contacts")
      .update({ status: blocked ? "blocked" : "accepted" })
      .eq("user_id", userId)
      .eq("contact_id", contactId),
  );
}

/** Elimina el contacto en los dos sentidos. */
export async function removeContact(contactId: string) {
  check(await supabase.rpc("remove_contact", { p_contact: contactId }));
}

// ---------- Perfil y avatar ----------

export interface Profile {
  displayName: string;
  avatarPath: string | null;
  avatarUrl: string | null;
}

async function signAvatars(paths: (string | null)[]): Promise<Map<string, string>> {
  const list = [...new Set(paths.filter((p): p is string => !!p))];
  const urls = new Map<string, string>();
  if (list.length === 0) return urls;
  const { data } = await supabase.storage.from("avatars").createSignedUrls(list, 3600);
  for (const d of data ?? []) if (d.path && d.signedUrl) urls.set(d.path, d.signedUrl);
  return urls;
}

export async function getProfile(userId: string): Promise<Profile> {
  const row = check(
    await supabase.from("profiles").select("display_name, avatar_path").eq("id", userId).single(),
  ) as { display_name: string; avatar_path: string | null };
  const urls = await signAvatars([row.avatar_path]);
  return {
    displayName: row.display_name,
    avatarPath: row.avatar_path,
    avatarUrl: row.avatar_path ? urls.get(row.avatar_path) ?? null : null,
  };
}

export async function setDisplayName(userId: string, name: string) {
  check(await supabase.from("profiles").update({ display_name: name }).eq("id", userId));
}

/** Sube un avatar nuevo (JPEG ya recortado) y borra el anterior. */
export async function setAvatar(userId: string, jpeg: Blob, previous: string | null) {
  const path = `${userId}/${crypto.randomUUID()}.jpg`;
  check(await supabase.storage.from("avatars").upload(path, jpeg, { contentType: "image/jpeg" }));
  check(await supabase.from("profiles").update({ avatar_path: path }).eq("id", userId));
  if (previous) await supabase.storage.from("avatars").remove([previous]);
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

export interface InboxMessage {
  id: string;
  type: MessageType;
  storage_path: string | null;
  thumb_path: string | null;
  caption: string | null;
  sender_id: string;
  sender_name: string;
  sent_at: string;
  seen: boolean;
  reaction: string | null;
}

/** Lo que le falta a este dispositivo (últimos 180 días), del más antiguo al más nuevo. */
export async function deviceInbox(device: string): Promise<InboxMessage[]> {
  return check(await supabase.rpc("device_inbox", { p_device: device })) as InboxMessage[];
}

/** Este dispositivo ya lo tiene (y, la primera vez, cuenta como entregado). */
export async function markDeviceDelivered(device: string, messageId: string) {
  check(await supabase.rpc("mark_device_delivered", { p_device: device, p_message: messageId }));
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

// ---------- Enviados ----------

export interface SentRecipient {
  id: string;
  name: string;
  delivered: boolean;
  seen: boolean;
  reaction: string | null;
}

export interface SentItem {
  id: string;
  type: MessageType;
  caption: string | null;
  thumbPath: string | null;
  sentAt: string;
  recipients: SentRecipient[];
  thumbUrl: string | null;
}

export async function listSent(limit = 30): Promise<SentItem[]> {
  const rows = check(await supabase.rpc("my_sent", { p_limit: limit })) as {
    id: string;
    type: MessageType;
    caption: string | null;
    thumb_path: string | null;
    sent_at: string;
    recipients: SentRecipient[];
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
    thumbPath: r.thumb_path,
    sentAt: r.sent_at,
    recipients: r.recipients,
    thumbUrl: r.thumb_path ? urls.get(r.thumb_path) ?? null : null,
  }));
}
