import { useEffect, useState } from "react";
import QRCode from "qrcode";
import { acceptInvite, createInvite, removeContact, renameContact, setBlocked } from "../lib/api";
import type { Contact } from "../lib/types";
import Avatar from "./Avatar";

interface Props {
  userId: string;
  contacts: Contact[];
  selected: Set<string>;
  busy: boolean;
  onToggle: (id: string) => void;
  onPing: (id: string) => void;
  onChanged: () => Promise<void>;
  guard: (fn: () => Promise<void>, ok?: string) => Promise<void>;
}

export default function ContactsPanel({ userId, contacts, selected, busy, onToggle, onPing, onChanged, guard }: Props) {
  const [invite, setInvite] = useState<string | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [open, setOpen] = useState<string | null>(null);

  useEffect(() => {
    if (!invite) return setQr(null);
    // El QR solo contiene el código (DECISIONS §3).
    QRCode.toDataURL(invite, { margin: 1, width: 180, color: { dark: "#000000", light: "#ffffff" } })
      .then(setQr)
      .catch(console.error);
  }, [invite]);

  return (
    <section className="card">
      <h2>Contactos</h2>
      <div className="row">
        <button disabled={busy} onClick={() => guard(async () => setInvite(await createInvite()))}>
          Generar código
        </button>
        {invite && <code className="invite">{invite}</code>}
      </div>
      {qr && (
        <div className="qr">
          <img src={qr} alt={`QR del código ${invite}`} />
          <span className="muted">Escanéalo desde el móvil. Vale 24 h y un solo uso.</span>
        </div>
      )}
      <div className="row">
        <input
          placeholder="Código de tu contacto"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={6}
        />
        <button
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

      <ul className="contacts">
        {contacts.map((c) => (
          <li key={c.contactId}>
            <div className="contact-row">
              <label>
                <input
                  type="checkbox"
                  checked={selected.has(c.contactId)}
                  onChange={() => onToggle(c.contactId)}
                  disabled={c.status === "blocked"}
                />
                <Avatar url={c.avatarUrl} name={c.name} />
                <span>
                  {c.name}
                  {c.nickname && <span className="muted"> · {c.displayName}</span>}
                  {c.status === "blocked" && <span className="muted"> · bloqueado</span>}
                </span>
              </label>
              <div className="row">
                <button
                  className="chip"
                  disabled={busy || c.status === "blocked"}
                  onClick={() => onPing(c.contactId)}
                  title="Pienso en ti"
                >
                  💭
                </button>
                <button className="chip" onClick={() => setOpen(open === c.contactId ? null : c.contactId)}>
                  ···
                </button>
              </div>
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
        {contacts.length === 0 && <li className="muted">Aún no tienes contactos</li>}
      </ul>
    </section>
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
      <div className="row">
        <input
          placeholder={`Apodo (vacío = ${contact.displayName})`}
          value={name}
          maxLength={40}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="chip" disabled={busy} onClick={() => onRename(name.trim() || null)}>
          Guardar
        </button>
      </div>
      <div className="row">
        <button className="chip" disabled={busy} onClick={() => onBlock(!blocked)}>
          {blocked ? "Desbloquear" : "Bloquear"}
        </button>
        {confirm ? (
          <>
            <span className="muted">Se borra para los dos. ¿Seguro?</span>
            <button disabled={busy} onClick={onRemove}>
              Sí, eliminar
            </button>
            <button className="chip" onClick={() => setConfirm(false)}>
              No
            </button>
          </>
        ) : (
          <button className="chip" disabled={busy} onClick={() => setConfirm(true)}>
            Eliminar
          </button>
        )}
      </div>
    </div>
  );
}
