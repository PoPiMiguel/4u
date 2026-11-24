// Envía push FCM a los dispositivos Android implicados.
// Llamada por triggers de Postgres (private.call_function) con:
//   { kind: "message", message_id }                 → a los destinatarios
//   { kind: "receipt", message_id, recipient_id }   → al emisor (reacción / visto)
// El escritorio no usa FCM: se entera por Supabase Realtime.

import { admin, isAuthorized } from "../_shared/admin.ts";
import { sendFcm } from "../_shared/fcm.ts";

async function pushToUsers(userIds: string[], data: Record<string, string>) {
  if (userIds.length === 0) return;
  const { data: devices, error } = await admin
    .from("devices")
    .select("id, fcm_token")
    .in("user_id", userIds)
    .eq("platform", "android")
    .not("fcm_token", "is", null);
  if (error) throw error;

  await Promise.all((devices ?? []).map(async (d) => {
    const result = await sendFcm(d.fcm_token as string, data);
    if (result === "unregistered") {
      await admin.from("devices").update({ fcm_token: null }).eq("id", d.id);
    }
  }));
}

async function onMessage(messageId: string) {
  // deno-lint-ignore no-explicit-any
  const { data: msg, error } = await admin
    .from("messages")
    .select("id, type, caption, sender_id, status, sender:profiles!messages_sender_id_fkey(display_name), message_recipients(recipient_id)")
    .eq("id", messageId)
    .single<any>();
  if (error) throw error;
  if (msg.status !== "sent") return;

  const recipients: string[] = (msg.message_recipients ?? []).map((r: { recipient_id: string }) => r.recipient_id);
  await pushToUsers(recipients, {
    kind: "message",
    message_id: msg.id,
    type: msg.type,
    sender_id: msg.sender_id,
    sender_name: msg.sender?.display_name ?? "",
    caption: msg.caption ?? "",
  });
}

async function onReceipt(messageId: string, recipientId: string) {
  // deno-lint-ignore no-explicit-any
  const { data: row, error } = await admin
    .from("message_recipients")
    .select("reaction, seen_at, recipient:profiles!message_recipients_recipient_id_fkey(display_name), message:messages!message_recipients_message_id_fkey(sender_id)")
    .eq("message_id", messageId)
    .eq("recipient_id", recipientId)
    .single<any>();
  if (error) throw error;

  await pushToUsers([row.message.sender_id], {
    kind: "receipt",
    message_id: messageId,
    recipient_id: recipientId,
    recipient_name: row.recipient?.display_name ?? "",
    reaction: row.reaction ?? "",
    seen: row.seen_at ? "1" : "0",
  });
}

Deno.serve(async (req) => {
  if (!isAuthorized(req)) return new Response("forbidden", { status: 403 });

  try {
    const body = await req.json();
    if (body.kind === "message") await onMessage(body.message_id);
    else if (body.kind === "receipt") await onReceipt(body.message_id, body.recipient_id);
    else return new Response("unknown kind", { status: 400 });
    return new Response("ok");
  } catch (e) {
    console.error(e);
    return new Response(String(e), { status: 500 });
  }
});
