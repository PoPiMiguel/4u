import { useState } from "react";
import type { OutPending, SentItem } from "../lib/api";

const TYPE_LABEL: Record<string, string> = {
  ping: "💭",
  html: "HTML",
  drawing: "Dibujo",
  video: "Vídeo",
  gif: "GIF",
  photo: "Foto",
};

function Thumb({ url, type }: { url: string | null; type: string }) {
  return (
    <div className="pending-media">{url ? <img className="media" src={url} alt="" /> : <span>{TYPE_LABEL[type] ?? "?"}</span>}</div>
  );
}

const fmt = (iso: string) => new Date(iso).toLocaleString([], { dateStyle: "short", timeStyle: "short" });

/** Programados o sin entregar todavía, con opción de cancelar. */
export function PendingPanel({
  items,
  busy,
  onCancel,
}: {
  items: OutPending[];
  busy: boolean;
  onCancel: (p: OutPending) => void;
}) {
  const [confirm, setConfirm] = useState<string | null>(null);
  if (items.length === 0) return null;

  return (
    <section className="card">
      <h2>En espera</h2>
      <ul className="pending">
        {items.map((p) => (
          <li key={p.id}>
            <Thumb url={p.thumbUrl} type={p.type} />
            <div className="pending-info">
              <strong>Para {p.recipients.map((r) => r.name).join(", ")}</strong>
              {p.caption && <p>{p.caption}</p>}
              <span className="muted">
                {p.status === "scheduled"
                  ? `Programado: ${fmt(p.deliverAt)}`
                  : `Sin entregar a ${p.recipients.filter((r) => !r.delivered).map((r) => r.name).join(", ")}`}
              </span>
            </div>
            {confirm === p.id ? (
              <div className="row">
                <span className="muted">¿Cancelar?</span>
                <button
                  disabled={busy}
                  onClick={() => {
                    setConfirm(null);
                    onCancel(p);
                  }}
                >
                  Sí
                </button>
                <button className="chip" onClick={() => setConfirm(null)}>
                  No
                </button>
              </div>
            ) : (
              <button className="chip" disabled={busy} onClick={() => setConfirm(p.id)}>
                Cancelar
              </button>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Lo último que he enviado y qué ha pasado con cada destinatario. */
export function SentPanel({ items, localThumbs }: { items: SentItem[]; localThumbs: Map<string, string> }) {
  if (items.length === 0) return null;
  return (
    <section className="card">
      <h2>Enviados</h2>
      <ul className="pending">
        {items.map((s) => (
          <li key={s.id} className="sent">
            <Thumb url={localThumbs.get(s.id) ?? s.thumbUrl} type={s.type} />
            <div className="pending-info">
              {s.caption && <p>{s.caption}</p>}
              <span className="muted">{fmt(s.sentAt)}</span>
              <div className="receipts">
                {s.recipients.map((r) => (
                  <span key={r.id} className="receipt" title={receiptTitle(r)}>
                    {r.name}
                    <span className={`state ${r.seen ? "seen" : r.delivered ? "delivered" : "waiting"}`}>
                      {r.seen ? "Visto" : r.delivered ? "Entregado" : "Enviado"}
                    </span>
                    {r.reaction && <span>{r.reaction}</span>}
                  </span>
                ))}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

function receiptTitle(r: SentItem["recipients"][number]): string {
  if (r.seen) return "Lo ha visto";
  if (r.delivered) return "Le ha llegado";
  return "Aún no le ha llegado";
}
