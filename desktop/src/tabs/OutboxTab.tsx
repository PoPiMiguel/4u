import { useState } from "react";
import type { OutPending, SentItem } from "../lib/api";
import Icon from "../components/Icon";
import { dateTime, relativeTime } from "../lib/time";

const TYPE_LABEL: Record<string, string> = {
  ping: "Toque",
  html: "HTML",
  drawing: "Dibujo",
  video: "Vídeo",
  gif: "GIF",
  photo: "Foto",
};

function Thumb({ url, type }: { url: string | null; type: string }) {
  return (
    <div className="thumb">
      {url ? (
        <img src={url} alt="" />
      ) : type === "ping" ? (
        <Icon name="pulse" size={22} />
      ) : (
        <span>{TYPE_LABEL[type] ?? "?"}</span>
      )}
    </div>
  );
}

interface Props {
  pending: OutPending[];
  sent: SentItem[];
  localThumbs: Map<string, string>;
  busy: boolean;
  onCancel: (p: OutPending) => void;
}

/** En espera (programados o sin entregar, con cancelar) y lo ya enviado. */
export default function OutboxTab({ pending, sent, localThumbs, busy, onCancel }: Props) {
  const [confirm, setConfirm] = useState<string | null>(null);

  if (pending.length === 0 && sent.length === 0) {
    return (
      <div className="empty">
        <Icon name="outbox" size={36} />
        <p>Aquí verás lo que envíes y si ya lo han visto.</p>
      </div>
    );
  }

  return (
    <div className="tab-body">
      {pending.length > 0 && (
        <section className="block">
          <h3>En espera</h3>
          <ul className="rows">
            {pending.map((p) => (
              <li key={p.id}>
                <Thumb url={localThumbs.get(p.id) ?? p.thumbUrl} type={p.type} />
                <div className="row-text">
                  <strong>Para {p.recipients.map((r) => r.name).join(", ")}</strong>
                  {p.caption && <span className="clip">{p.caption}</span>}
                  <span className="muted">
                    {p.status === "scheduled"
                      ? `Sale el ${dateTime(p.deliverAt)}`
                      : `Sin entregar a ${p.recipients.filter((r) => !r.delivered).map((r) => r.name).join(", ")}`}
                  </span>
                </div>
                {confirm === p.id ? (
                  <div className="confirm">
                    <button
                      className="danger small"
                      disabled={busy}
                      onClick={() => {
                        setConfirm(null);
                        onCancel(p);
                      }}
                    >
                      Cancelar envío
                    </button>
                    <button className="ghost small" onClick={() => setConfirm(null)}>
                      No
                    </button>
                  </div>
                ) : (
                  <button className="ghost small" disabled={busy} onClick={() => setConfirm(p.id)}>
                    Cancelar
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {sent.length > 0 && (
        <section className="block">
          <h3>Enviados</h3>
          <ul className="rows">
            {sent.map((s) => (
              <li key={s.id}>
                <Thumb url={localThumbs.get(s.id) ?? s.thumbUrl} type={s.type} />
                <div className="row-text">
                  {s.caption && <span className="clip">{s.caption}</span>}
                  <span className="muted">{relativeTime(s.sentAt)}</span>
                  <div className="receipts">
                    {s.recipients.map((r) => (
                      <span key={r.id} className="receipt">
                        {r.name}
                        <span className={`state ${r.seen ? "seen" : r.delivered ? "delivered" : "waiting"}`}>
                          {r.seen ? "Visto" : r.delivered ? "Entregado" : "Enviado"}
                        </span>
                        {r.reaction && <span className="reaction-mini">{r.reaction}</span>}
                      </span>
                    ))}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
