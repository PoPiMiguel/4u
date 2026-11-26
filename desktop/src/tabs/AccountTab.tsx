import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { emit } from "@tauri-apps/api/event";
import { getVersion } from "@tauri-apps/api/app";
import { disable, enable, isEnabled } from "@tauri-apps/plugin-autostart";
import type { Update } from "@tauri-apps/plugin-updater";
import { getProfile, setAvatar, setDisplayName, type Profile } from "../lib/api";
import { avatarJpeg } from "../lib/media";
import { findUpdate, installUpdate } from "../lib/updates";
import { signOutClean } from "../lib/session";
import type { WidgetSize } from "../lib/types";
import Avatar from "../components/Avatar";
import Icon from "../components/Icon";

interface Props {
  userId: string;
  busy: boolean;
  guard: (fn: () => Promise<void>, ok?: string) => Promise<void>;
}

const SIZES: [WidgetSize, string][] = [
  ["small", "Pequeño"],
  ["medium", "Mediano"],
  ["large", "Grande"],
];

export default function AccountTab({ userId, busy, guard }: Props) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [name, setName] = useState("");
  const [autostart, setAutostart] = useState<boolean | null>(null);
  const [version, setVersion] = useState("");
  const [update, setUpdate] = useState<Update | null>(null);
  const [checked, setChecked] = useState(false);
  const [size, setSize] = useState<WidgetSize>(() => (localStorage.getItem("4u.widgetSize") as WidgetSize) || "small");

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

  const changeSize = (s: WidgetSize) =>
    guard(async () => {
      localStorage.setItem("4u.widgetSize", s);
      setSize(s);
      await emit("widget:size", s);
    });

  return (
    <div className="tab-body">
      <section className="block profile">
        <Avatar url={profile?.avatarUrl ?? null} name={profile?.displayName ?? "?"} size={72} />
        <div className="profile-fields">
          <div className="inline">
            <input value={name} maxLength={40} onChange={(e) => setName(e.target.value)} placeholder="Tu nombre" />
            <button
              className="ghost"
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
          <label className="ghost small file-button">
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
      </section>

      <section className="block">
        <h3>Widget</h3>
        <div className="segmented">
          {SIZES.map(([s, label]) => (
            <button key={s} className={size === s ? "on" : ""} onClick={() => changeSize(s)}>
              {label}
            </button>
          ))}
        </div>
        <button className="ghost" onClick={() => guard(async () => void (await invoke("toggle_widget")))}>
          <Icon name="widget" size={18} />
          Mostrar u ocultar
        </button>
      </section>

      <section className="block">
        <h3>Sistema</h3>
        {autostart !== null && (
          <label className="switch">
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
            <span>Abrir al iniciar sesión (en la bandeja)</span>
          </label>
        )}
        <div className="inline">
          <span className="muted">Versión {version}</span>
          {update ? (
            <button className="primary small" disabled={busy} onClick={() => guard(() => installUpdate(update), "Actualizando…")}>
              Instalar la {update.version}
            </button>
          ) : (
            <button
              className="ghost small"
              disabled={busy}
              onClick={() =>
                guard(async () => {
                  setUpdate(await findUpdate());
                  setChecked(true);
                })
              }
            >
              Buscar actualizaciones
            </button>
          )}
        </div>
        {checked && !update && <p className="muted">Estás al día (o se actualiza con el gestor de paquetes).</p>}
      </section>

      <button className="ghost danger-text" onClick={() => signOutClean()}>
        <Icon name="logout" size={18} />
        Cerrar sesión
      </button>
    </div>
  );
}
