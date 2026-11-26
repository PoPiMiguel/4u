import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { emit } from "@tauri-apps/api/event";
import { errorMessage } from "../lib/supabase";
import {
  cancelOutPending,
  listContacts,
  listOutPending,
  listSent,
  markSeen,
  react,
  sendMessage,
  type OutPending,
  type SentItem,
} from "../lib/api";
import { prepareFile, tinyDataUrl, type PreparedFile } from "../lib/media";
import { renderJpeg, type Drawing } from "../lib/drawing";
import { subscribeInbox, syncInbox } from "../lib/inbox";
import { loadHistory, updateHistoryItem } from "../lib/store";
import { rememberSentThumb, sentThumbs } from "../lib/sentCache";
import type { Contact, HistoryItem, WidgetSize } from "../lib/types";
import MediaView from "../components/MediaView";
import ContactsPanel from "../components/ContactsPanel";
import AccountPanel from "../components/AccountPanel";
import DrawPad from "../components/DrawPad";
import { PendingPanel, SentPanel } from "../components/OutgoingPanels";

const REACTIONS = ["❤️", "😂", "😮", "😢", "🔥"];

export default function Home({ userId }: { userId: string }) {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [history, setHistory] = useState<HistoryItem[]>(loadHistory);
  const [file, setFile] = useState<File | null>(null);
  const [drawing, setDrawing] = useState<Drawing | null>(null);
  const [drawingOpen, setDrawingOpen] = useState(false);
  const [caption, setCaption] = useState("");
  const [when, setWhen] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [outPending, setOutPending] = useState<OutPending[]>([]);
  const [sent, setSent] = useState<SentItem[]>([]);
  const [localThumbs, setLocalThumbs] = useState(sentThumbs);

  const refreshHistory = () => setHistory(loadHistory());
  const refreshContacts = () => listContacts(userId).then(setContacts);
  const refreshOutgoing = async () => {
    try {
      const [p, s] = await Promise.all([listOutPending(), listSent()]);
      setOutPending(p);
      setSent(s);
      setLocalThumbs(sentThumbs());
    } catch (e) {
      console.error(e);
    }
  };

  async function guard(fn: () => Promise<void>, ok?: string) {
    setBusy(true);
    setStatus(null);
    try {
      await fn();
      if (ok) setStatus(ok);
    } catch (e) {
      setStatus(`Error: ${errorMessage(e)}`);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    refreshContacts().catch((e) => setStatus(errorMessage(e)));
    syncInbox(userId).then(refreshHistory).catch(console.error);
    refreshOutgoing();
    // Programados que salen, entregas, vistos y contactos que cambian desde el otro lado.
    const timer = setInterval(() => {
      refreshOutgoing();
      refreshContacts().catch(console.error);
    }, 15_000);
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
    const list = [...selected].filter((id) => contacts.some((c) => c.contactId === id && c.status === "accepted"));
    if (list.length === 0) throw new Error("Elige al menos un contacto");
    return list;
  };

  async function prepared(): Promise<PreparedFile> {
    if (drawing) {
      const json = new Blob([JSON.stringify(drawing)], { type: "application/json" });
      return { type: "drawing", file: json, ext: "json", mime: "application/json", thumb: await renderJpeg(drawing) };
    }
    if (!file) throw new Error("Elige un archivo o haz un dibujo");
    return prepareFile(file);
  }

  const send = () =>
    guard(async () => {
      const to = recipients();
      const deliverAt = when ? new Date(when) : null;
      if (deliverAt && deliverAt.getTime() <= Date.now()) throw new Error("Esa hora ya ha pasado");
      const p = await prepared();
      const id = await sendMessage(userId, {
        type: p.type,
        recipients: to,
        file: p.file,
        ext: p.ext,
        mime: p.mime,
        thumb: p.thumb,
        caption: caption.trim() || null,
        deliverAt,
      });
      if (p.thumb) rememberSentThumb(id, await tinyDataUrl(p.thumb).catch(() => ""));
      setFile(null);
      setDrawing(null);
      setCaption("");
      setWhen("");
      await refreshOutgoing();
    }, when ? "Programado" : "Enviado");

  const ping = (contactId: string) =>
    guard(async () => {
      await sendMessage(userId, { type: "ping", recipients: [contactId] });
      await refreshOutgoing();
    }, "Toque enviado");

  const cancel = (item: OutPending) =>
    guard(async () => {
      await cancelOutPending(item);
      await refreshOutgoing();
    }, "Envío cancelado");

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

  const ready = !!file || !!drawing;

  return (
    <div className="home">
      <header>
        <h1>4u</h1>
        <div className="row">
          <button onClick={() => guard(async () => void (await invoke("toggle_widget")))}>Widget</button>
          {(["small", "medium", "large"] as WidgetSize[]).map((s) => (
            <button key={s} onClick={() => setWidgetSize(s)}>
              {s === "small" ? "S" : s === "medium" ? "M" : "L"}
            </button>
          ))}
        </div>
      </header>

      {status && <p className="status">{status}</p>}

      <ContactsPanel
        userId={userId}
        contacts={contacts}
        selected={selected}
        busy={busy}
        onToggle={toggle}
        onPing={ping}
        onChanged={refreshContacts}
        guard={guard}
      />

      <section className="card">
        <h2>Enviar</h2>
        {drawingOpen ? (
          <DrawPad
            onCancel={() => setDrawingOpen(false)}
            onDone={(d) => {
              setDrawing(d);
              setFile(null);
              setDrawingOpen(false);
            }}
          />
        ) : (
          <>
            <div className="row">
              <label className="chip file-button">
                {file ? file.name : "Elegir archivo"}
                <input
                  type="file"
                  hidden
                  accept="image/*,video/*,.gif,.html,.htm"
                  onChange={(e) => {
                    setFile(e.target.files?.[0] ?? null);
                    setDrawing(null);
                    e.target.value = "";
                  }}
                />
              </label>
              <button className="chip" onClick={() => setDrawingOpen(true)}>
                {drawing ? "Nuevo dibujo" : "Dibujar"}
              </button>
              {drawing && (
                <button className="chip" onClick={() => setDrawing(null)}>
                  Quitar dibujo
                </button>
              )}
            </div>
            <input placeholder="Texto (opcional)" value={caption} onChange={(e) => setCaption(e.target.value)} maxLength={280} />
            <label className="muted">
              Programar (opcional)
              <input type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
            </label>
            <button disabled={busy || !ready || selected.size === 0} onClick={send}>
              {when ? "Programar" : "Enviar"} {drawing ? "dibujo " : ""}a {selected.size} contacto(s)
            </button>
          </>
        )}
      </section>

      <PendingPanel items={outPending} busy={busy} onCancel={cancel} />
      <SentPanel items={sent} localThumbs={localThumbs} />

      <section className="card">
        <h2>Recibidos</h2>
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

      <AccountPanel userId={userId} busy={busy} guard={guard} />
    </div>
  );
}
