import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { emit } from "@tauri-apps/api/event";
import { errorMessage, supabase } from "../lib/supabase";
import {
  acceptInvite,
  cancelOutPending,
  createInvite,
  listContacts,
  listOutPending,
  markSeen,
  react,
  sendMessage,
  type OutPending,
} from "../lib/api";
import { prepareFile } from "../lib/media";
import { subscribeInbox, syncInbox } from "../lib/inbox";
import { loadHistory, updateHistoryItem } from "../lib/store";
import type { Contact, HistoryItem, WidgetSize } from "../lib/types";
import MediaView from "../components/MediaView";

const REACTIONS = ["❤️", "😂", "😮", "😢", "🔥"];

const TYPE_EMOJI: Record<string, string> = {
  ping: "💭",
  html: "🌸",
  drawing: "🎨",
  video: "🎬",
  gif: "✨",
  photo: "📷",
};

function pendingLabel(p: OutPending): string {
  if (p.status === "scheduled") {
    return `📅 ${new Date(p.deliverAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" })}`;
  }
  const waiting = p.recipients.filter((r) => !r.delivered).map((r) => r.name);
  return `⏳ Sin entregar a ${waiting.join(", ")}`;
}

export default function Home({ userId }: { userId: string }) {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [history, setHistory] = useState<HistoryItem[]>(loadHistory);
  const [invite, setInvite] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [caption, setCaption] = useState("");
  const [when, setWhen] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [outPending, setOutPending] = useState<OutPending[]>([]);
  const [confirmCancel, setConfirmCancel] = useState<string | null>(null);

  const refreshHistory = () => setHistory(loadHistory());
  const refreshContacts = () => listContacts(userId).then(setContacts);
  const refreshOutPending = () => listOutPending().then(setOutPending).catch(console.error);

  async function guard(fn: () => Promise<void>) {
    setBusy(true);
    setStatus(null);
    try {
      await fn();
    } catch (e) {
      setStatus(`⚠️ ${errorMessage(e)}`);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    refreshContacts().catch((e) => setStatus(errorMessage(e)));
    syncInbox(userId).then(refreshHistory).catch(console.error);
    refreshOutPending();
    // Los programados salen solos y los destinatarios los van recibiendo: refresco periódico.
    const timer = setInterval(refreshOutPending, 15_000);
    const unsubscribe = subscribeInbox(userId, refreshHistory);
    return () => {
      clearInterval(timer);
      unsubscribe();
    };
  }, [userId]);

  // Marcar como visto lo que aparece en pantalla.
  useEffect(() => {
    for (const item of history) {
      if (!item.seen) {
        updateHistoryItem(item.id, { seen: true });
        markSeen(item.id, userId).catch(console.error);
      }
    }
  }, [history, userId]);

  function toggle(id: string) {
    const next = new Set(selected);
    next.has(id) ? next.delete(id) : next.add(id);
    setSelected(next);
  }

  const recipients = () => {
    const list = [...selected];
    if (list.length === 0) throw new Error("Elige al menos un contacto");
    return list;
  };

  const send = () =>
    guard(async () => {
      const to = recipients();
      if (!file) throw new Error("Elige un archivo");
      const p = await prepareFile(file);
      await sendMessage(userId, {
        type: p.type,
        recipients: to,
        file: p.file,
        ext: p.ext,
        mime: p.mime,
        thumb: p.thumb,
        caption: caption.trim() || null,
        deliverAt: when ? new Date(when) : null,
      });
      setFile(null);
      setCaption("");
      setWhen("");
      setStatus(when ? "📅 Programado" : "✅ Enviado");
      await refreshOutPending();
    });

  const ping = (contactId: string) =>
    guard(async () => {
      await sendMessage(userId, { type: "ping", recipients: [contactId] });
      setStatus("💭 Toque enviado");
      await refreshOutPending();
    });

  const cancel = (item: OutPending) =>
    guard(async () => {
      setConfirmCancel(null);
      await cancelOutPending(item);
      setStatus("🗑️ Envío cancelado");
      await refreshOutPending();
    });

  const reactTo = (item: HistoryItem, emoji: string) =>
    guard(async () => {
      const value = item.reaction === emoji ? null : emoji;
      await react(item.id, userId, value);
      updateHistoryItem(item.id, { reaction: value });
      refreshHistory();
    });

  const setWidgetSize = (size: WidgetSize) =>
    guard(async () => {
      localStorage.setItem("4u.widgetSize", size);
      await emit("widget:size", size);
    });

  return (
    <div className="home">
      <header>
        <h1>4u 💌</h1>
        <div className="row">
          <button onClick={() => guard(async () => void (await invoke("toggle_widget")))}>Widget</button>
          {(["small", "medium", "large"] as WidgetSize[]).map((s) => (
            <button key={s} onClick={() => setWidgetSize(s)}>
              {s === "small" ? "S" : s === "medium" ? "M" : "L"}
            </button>
          ))}
          <button className="link" onClick={() => supabase.auth.signOut()}>
            Salir
          </button>
        </div>
      </header>

      {status && <p className="status">{status}</p>}

      <section className="card">
        <h2>Contactos</h2>
        <div className="row">
          <button disabled={busy} onClick={() => guard(async () => setInvite(await createInvite()))}>
            Generar código
          </button>
          {invite && <code className="invite">{invite}</code>}
        </div>
        <div className="row">
          <input placeholder="Código de tu contacto" value={code} onChange={(e) => setCode(e.target.value)} maxLength={6} />
          <button
            disabled={busy || code.length < 6}
            onClick={() =>
              guard(async () => {
                await acceptInvite(code);
                setCode("");
                await refreshContacts();
              })
            }
          >
            Añadir
          </button>
        </div>
        <ul className="contacts">
          {contacts.map((c) => (
            <li key={c.contactId}>
              <label>
                <input
                  type="checkbox"
                  checked={selected.has(c.contactId)}
                  onChange={() => toggle(c.contactId)}
                  disabled={c.status === "blocked"}
                />
                {c.name} {c.status === "blocked" && "(bloqueado)"}
              </label>
              <button disabled={busy || c.status === "blocked"} onClick={() => ping(c.contactId)} title="Pienso en ti">
                💭
              </button>
            </li>
          ))}
          {contacts.length === 0 && <li className="muted">Aún no tienes contactos</li>}
        </ul>
      </section>

      <section className="card">
        <h2>Enviar</h2>
        <input
          type="file"
          accept="image/*,video/*,.gif,.html,.htm"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        <input placeholder="Texto (opcional)" value={caption} onChange={(e) => setCaption(e.target.value)} maxLength={280} />
        <label className="muted">
          Programar (opcional)
          <input type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
        </label>
        <button disabled={busy || !file || selected.size === 0} onClick={send}>
          {when ? "Programar" : "Enviar"} a {selected.size} contacto(s)
        </button>
      </section>

      {outPending.length > 0 && (
        <section className="card">
          <h2>En espera</h2>
          <ul className="pending">
            {outPending.map((p) => (
              <li key={p.id}>
                <div className="pending-media">
                  {p.thumbUrl ? <img className="media" src={p.thumbUrl} alt="" /> : <span>{TYPE_EMOJI[p.type] ?? "📦"}</span>}
                </div>
                <div className="pending-info">
                  <strong>Para {p.recipients.map((r) => r.name).join(", ")}</strong>
                  {p.caption && <p>{p.caption}</p>}
                  <span className="muted">{pendingLabel(p)}</span>
                </div>
                {confirmCancel === p.id ? (
                  <div className="row">
                    <span className="muted">¿Cancelar?</span>
                    <button disabled={busy} onClick={() => cancel(p)}>
                      Sí
                    </button>
                    <button className="chip" onClick={() => setConfirmCancel(null)}>
                      No
                    </button>
                  </div>
                ) : (
                  <button className="chip" disabled={busy} onClick={() => setConfirmCancel(p.id)}>
                    Cancelar
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="card">
        <h2>Historial</h2>
        <ul className="history">
          {history.map((item) => (
            <li key={item.id}>
              <div className="history-media">
                <MediaView item={item} />
              </div>
              <div>
                <strong>{item.senderName}</strong>{" "}
                <span className="muted">{new Date(item.receivedAt).toLocaleString()}</span>
                {item.caption && <p>{item.caption}</p>}
                <div className="row">
                  {REACTIONS.map((r) => (
                    <button key={r} className={item.reaction === r ? "chip on" : "chip"} onClick={() => reactTo(item, r)}>
                      {r}
                    </button>
                  ))}
                </div>
              </div>
            </li>
          ))}
          {history.length === 0 && <li className="muted">Nada por aquí todavía</li>}
        </ul>
      </section>
    </div>
  );
}
