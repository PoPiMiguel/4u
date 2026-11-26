import { useState } from "react";
import { sendMessage } from "../lib/api";
import { prepareFile, tinyDataUrl, type PreparedFile } from "../lib/media";
import { renderJpeg, type Drawing } from "../lib/drawing";
import { rememberSentThumb } from "../lib/sentCache";
import type { Contact } from "../lib/types";
import Avatar from "../components/Avatar";
import DrawPad from "../components/DrawPad";
import Icon from "../components/Icon";

interface Props {
  userId: string;
  contacts: Contact[];
  busy: boolean;
  guard: (fn: () => Promise<void>, ok?: string) => Promise<void>;
  onSent: () => Promise<void>;
}

export default function ComposeTab({ userId, contacts, busy, guard, onSent }: Props) {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [file, setFile] = useState<File | null>(null);
  const [drawing, setDrawing] = useState<Drawing | null>(null);
  const [drawingOpen, setDrawingOpen] = useState(false);
  const [caption, setCaption] = useState("");
  const [when, setWhen] = useState("");

  const available = contacts.filter((c) => c.status === "accepted");
  const to = [...selected].filter((id) => available.some((c) => c.contactId === id));

  function toggle(id: string) {
    const next = new Set(selected);
    next.has(id) ? next.delete(id) : next.add(id);
    setSelected(next);
  }

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
      if (to.length === 0) throw new Error("Elige al menos un contacto");
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
      await onSent();
    }, when ? "Programado" : "Enviado");

  const ping = () =>
    guard(async () => {
      if (to.length === 0) throw new Error("Elige al menos un contacto");
      await sendMessage(userId, { type: "ping", recipients: to });
      await onSent();
    }, "Toque enviado");

  if (drawingOpen) {
    return (
      <div className="tab-body">
        <DrawPad
          onCancel={() => setDrawingOpen(false)}
          onDone={(d) => {
            setDrawing(d);
            setFile(null);
            setDrawingOpen(false);
          }}
        />
      </div>
    );
  }

  return (
    <div className="tab-body">
      <section className="block">
        <h3>Para</h3>
        {available.length === 0 ? (
          <p className="muted">Añade a alguien en Contactos.</p>
        ) : (
          <div className="chips">
            {available.map((c) => (
              <button
                key={c.contactId}
                className={`person${selected.has(c.contactId) ? " on" : ""}`}
                onClick={() => toggle(c.contactId)}
                aria-pressed={selected.has(c.contactId)}
              >
                <Avatar url={c.avatarUrl} name={c.name} size={26} />
                {c.name}
              </button>
            ))}
          </div>
        )}
      </section>

      <section className="block">
        <h3>Qué</h3>
        <div className="choices">
          <label className={`choice${file ? " on" : ""}`}>
            <Icon name="image" size={22} />
            <span>{file ? file.name : "Foto, vídeo, GIF o HTML"}</span>
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
          <button className={`choice${drawing ? " on" : ""}`} onClick={() => setDrawingOpen(true)}>
            <Icon name="pencil" size={22} />
            <span>{drawing ? "Dibujo listo (volver a dibujar)" : "Dibujar"}</span>
          </button>
        </div>
        {(file || drawing) && (
          <button
            className="ghost small"
            onClick={() => {
              setFile(null);
              setDrawing(null);
            }}
          >
            Quitar
          </button>
        )}
      </section>

      <section className="block">
        <input placeholder="Texto (opcional)" value={caption} onChange={(e) => setCaption(e.target.value)} maxLength={280} />
        <label className="schedule">
          <Icon name="calendar" size={18} />
          <span>Programar</span>
          <input type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
          {when && (
            <button className="ghost small" onClick={() => setWhen("")}>
              Quitar
            </button>
          )}
        </label>
      </section>

      <div className="actions">
        <button className="primary" disabled={busy || (!file && !drawing) || to.length === 0} onClick={send}>
          <Icon name="send" size={18} />
          {when ? "Programar" : "Enviar"}
        </button>
        <button className="ghost" disabled={busy || to.length === 0} onClick={ping} title="Un aviso de que piensas en esa persona">
          <Icon name="pulse" size={18} />
          Toque
        </button>
      </div>
    </div>
  );
}
