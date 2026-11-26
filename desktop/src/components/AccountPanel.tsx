import { useEffect, useState } from "react";
import { getVersion } from "@tauri-apps/api/app";
import { disable, enable, isEnabled } from "@tauri-apps/plugin-autostart";
import type { Update } from "@tauri-apps/plugin-updater";
import { getProfile, setAvatar, setDisplayName, type Profile } from "../lib/api";
import { avatarJpeg } from "../lib/media";
import { findUpdate, installUpdate } from "../lib/updates";
import { supabase } from "../lib/supabase";
import Avatar from "./Avatar";

interface Props {
  userId: string;
  busy: boolean;
  guard: (fn: () => Promise<void>, ok?: string) => Promise<void>;
}

export default function AccountPanel({ userId, busy, guard }: Props) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [name, setName] = useState("");
  const [autostart, setAutostart] = useState<boolean | null>(null);
  const [version, setVersion] = useState("");
  const [update, setUpdate] = useState<Update | null>(null);
  const [checked, setChecked] = useState(false);

  const reload = async () => {
    const p = await getProfile(userId);
    setProfile(p);
    setName(p.displayName);
  };

  useEffect(() => {
    reload().catch(console.error);
    isEnabled().then(setAutostart).catch(() => setAutostart(null));
    getVersion().then(setVersion).catch(console.error);
    findUpdate().then(setUpdate).catch(console.error);
  }, [userId]);

  return (
    <section className="card">
      <h2>Cuenta</h2>
      <div className="row">
        <Avatar url={profile?.avatarUrl ?? null} name={profile?.displayName ?? "?"} size={56} />
        <label className="chip file-button">
          Cambiar foto
          <input
            type="file"
            accept="image/*"
            hidden
            onChange={(e) => {
              const f = e.target.files?.[0];
              e.target.value = "";
              if (!f) return;
              guard(async () => {
                await setAvatar(userId, await avatarJpeg(f), profile?.avatarPath ?? null);
                await reload();
              }, "Foto cambiada");
            }}
          />
        </label>
      </div>
      <div className="row">
        <input value={name} maxLength={40} onChange={(e) => setName(e.target.value)} placeholder="Tu nombre" />
        <button
          className="chip"
          disabled={busy || !name.trim() || name.trim() === profile?.displayName}
          onClick={() =>
            guard(async () => {
              await setDisplayName(userId, name.trim());
              await reload();
            }, "Nombre cambiado")
          }
        >
          Guardar
        </button>
      </div>

      {autostart !== null && (
        <label className="row">
          <input
            type="checkbox"
            checked={autostart}
            onChange={(e) =>
              guard(async () => {
                if (e.target.checked) await enable();
                else await disable();
                setAutostart(await isEnabled());
              })
            }
          />
          Abrir 4u al iniciar sesión (en la bandeja, sin ventana)
        </label>
      )}

      <div className="row">
        <span className="muted">Versión {version}</span>
        {update ? (
          <button
            disabled={busy}
            onClick={() => guard(() => installUpdate(update), "Actualizando…")}
          >
            Instalar la {update.version}
          </button>
        ) : (
          <button
            className="chip"
            disabled={busy}
            onClick={() =>
              guard(async () => {
                const u = await findUpdate();
                setUpdate(u);
                setChecked(true);
              })
            }
          >
            Buscar actualizaciones
          </button>
        )}
        {checked && !update && <span className="muted">Estás al día (o se actualiza con el gestor de paquetes)</span>}
      </div>

      <button className="link" onClick={() => supabase.auth.signOut()}>
        Cerrar sesión
      </button>
    </section>
  );
}
