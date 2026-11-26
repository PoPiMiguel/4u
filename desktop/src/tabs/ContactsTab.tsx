import { useEffect, useState } from "react";
import QRCode from "qrcode";
import { acceptInvite, createInvite, removeContact, renameContact, setBlocked } from "../lib/api";
import type { Contact } from "../lib/types";
import Avatar from "../components/Avatar";
import Icon from "../components/Icon";

interface Props {
  userId: string;
  contacts: Contact[];
  busy: boolean;
  onPing: (id: string) => void;
  onChanged: () => Promise<void>;
  guard: (fn: () => Promise<void>, ok?: string) => Promise<void>;
}

export default function ContactsTab({ userId, contacts, busy, onPing, onChanged, guard }: Props) {
  const [invite, setInvite] = useState<string | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [open, setOpen] = useState<string | null>(null);

  useEffect(() => {
    if (!invite) return setQr(null);
    // El QR solo contiene el código (DECISIONS §3).
    QRCode.toDataURL(invite, { margin: 1, width: 220, color: { dark: "#000000", light: "#ffffff" } })
      .then(setQr)
      .catch(console.error);
  }, [invite]);

  return (
    <div className="tab-body">
      <section className="block">
        <h3>Añadir a alguien</h3>
        <div className="invite-box">
          {invite ? (
            <>
              {qr && <img className="qr" src={qr} alt={`QR del código ${invite}`} />}
              <div>
                <code className="code">{invite}</code>
                <p className="muted">Que lo escanee o lo escriba en su 4u. Vale 24 h y un solo uso.</p>
              </div>
            </>
          ) : (
            <button className="primary" disabled={busy} onClick={() => guard(async () => setInvite(await createInvite()))}>
              <Icon name="qr" size={18} />
              Generar mi código
            </button>
          )}
        </div>
        <div className="inline">
          <input
            placeholder="Código de la otra persona"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            maxLength={6}
          />
          <button
            className="primary"
            disabled={busy || code.length < 6}
            onClick={() =>
              guard(async () => {
                await acceptInvite(code);
                setCode("");
                await onChanged();
              }, "Contacto añadido")
            }
          >
            Añadir
          </button>
        </div>
      </section>

      <section className="block">
        <h3>Tus contactos</h3>
        {contacts.length === 0 && <p className="muted">Aún no tienes contactos.</p>}
        <ul className="rows">
          {contacts.map((c) => (
            <li key={c.contactId} className="contact">
              <div className="contact-main">
                <Avatar url={c.avatarUrl} name={c.name} size={40} />
                <div className="row-text">
                  <strong>{c.name}</strong>
                  {(c.nickname || c.status === "blocked") && (
                    <span className="muted">
                      {[c.nickname ? c.displayName : null, c.status === "blocked" ? "Bloqueado" : null]
                        .filter(Boolean)
                        .join(" · ")}
                    </span>
                  )}
                </div>
                <button
                  className="icon-button"
                  disabled={busy || c.status === "blocked"}
                  onClick={() => onPing(c.contactId)}
                  title="Enviar un toque"
                >
                  <Icon name="pulse" />
                </button>
                <button
                  className={`icon-button${open === c.contactId ? " on" : ""}`}
                  onClick={() => setOpen(open === c.contactId ? null : c.contactId)}
                  title="Opciones"
                >
                  <Icon name="more" />
                </button>
              </div>
              {open === c.contactId && (
                <ContactActions
                  contact={c}
                  busy={busy}
                  onRename={(name) =>
                    guard(async () => {
                      await renameContact(userId, c.contactId, name);
                      await onChanged();
                    }, "Nombre cambiado")
                  }
                  onBlock={(blocked) =>
                    guard(async () => {
                      await setBlocked(userId, c.contactId, blocked);
                      await onChanged();
                    }, blocked ? "Bloqueado" : "Desbloqueado")
                  }
                  onRemove={() =>
                    guard(async () => {
                      await removeContact(c.contactId);
                      setOpen(null);
                      await onChanged();
                    }, "Contacto eliminado")
                  }
                />
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function ContactActions({
  contact,
  busy,
  onRename,
  onBlock,
  onRemove,
}: {
  contact: Contact;
  busy: boolean;
  onRename: (name: string | null) => void;
  onBlock: (blocked: boolean) => void;
  onRemove: () => void;
}) {
  const [name, setName] = useState(contact.nickname ?? "");
  const [confirm, setConfirm] = useState(false);
  const blocked = contact.status === "blocked";

  return (
    <div className="contact-actions">
      <div className="inline">
        <input
          placeholder={`Apodo (vacío: ${contact.displayName})`}
          value={name}
          maxLength={40}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="ghost" disabled={busy} onClick={() => onRename(name.trim() || null)}>
          Guardar
        </button>
      </div>
      <div className="inline">
        <button className="ghost" disabled={busy} onClick={() => onBlock(!blocked)}>
          {blocked ? "Desbloquear" : "Bloquear"}
        </button>
        {confirm ? (
          <>
            <button className="danger" disabled={busy} onClick={onRemove}>
              Eliminar para los dos
            </button>
            <button className="ghost" onClick={() => setConfirm(false)}>
              No
            </button>
          </>
        ) : (
          <button className="ghost danger-text" disabled={busy} onClick={() => setConfirm(true)}>
            Eliminar
          </button>
        )}
      </div>
    </div>
  );
}
