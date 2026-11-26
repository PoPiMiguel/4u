import { useEffect, useRef, useState } from "react";
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
import { subscribeInbox, syncInbox } from "../lib/inbox";
import { loadHistory, pinnedId, setPinned, updateHistoryItem } from "../lib/store";
import { emit } from "@tauri-apps/api/event";
import { sentThumbs } from "../lib/sentCache";
import type { Contact, HistoryItem } from "../lib/types";
import Icon, { type IconName } from "../components/Icon";
import InboxTab from "../tabs/InboxTab";
import ComposeTab from "../tabs/ComposeTab";
import OutboxTab from "../tabs/OutboxTab";
import ContactsTab from "../tabs/ContactsTab";
import AccountTab from "../tabs/AccountTab";

const TABS: { key: string; label: string; icon: IconName }[] = [
  { key: "inbox", label: "Recibidos", icon: "inbox" },
  { key: "compose", label: "Enviar", icon: "send" },
  { key: "outbox", label: "Enviados", icon: "outbox" },
  { key: "contacts", label: "Contactos", icon: "users" },
  { key: "account", label: "Cuenta", icon: "user" },
];

export default function Home({ userId }: { userId: string }) {
  const [tab, setTab] = useState(0);
  const pager = useRef<HTMLDivElement>(null);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [history, setHistory] = useState<HistoryItem[]>(loadHistory);
  const [status, setStatus] = useState<{ text: string; error: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [outPending, setOutPending] = useState<OutPending[]>([]);
  const [sent, setSent] = useState<SentItem[]>([]);
  const [localThumbs, setLocalThumbs] = useState(sentThumbs);
  const [pinned, setPinnedState] = useState<string | null>(pinnedId);

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
      if (ok) setStatus({ text: ok, error: false });
    } catch (e) {
      setStatus({ text: errorMessage(e), error: true });
    } finally {
      setBusy(false);
    }
  }

  // El aviso se va solo.
  useEffect(() => {
    if (!status) return;
    const t = setTimeout(() => setStatus(null), status.error ? 6000 : 2500);
    return () => clearTimeout(t);
  }, [status]);

  useEffect(() => {
    refreshContacts().catch((e) => setStatus({ text: errorMessage(e), error: true }));
    syncInbox(userId).then(refreshHistory).catch(console.error);
    refreshOutgoing();
    // Programados que salen, entregas, vistos y contactos que cambian desde el otro lado.
    // Realtime avisa al momento, pero por si se pierde algo: cada 15 s.
    const timer = setInterval(() => {
      syncInbox(userId).then(refreshHistory).catch(console.error);
      refreshOutgoing();
      refreshContacts().catch(console.error);
    }, 15_000);
    const unsubscribe = subscribeInbox(userId, refreshHistory);
    return () => {
      clearInterval(timer);
      unsubscribe();
    };
  }, [userId]);

  // Lo recibido cuenta como visto cuando abres la pestaña Recibidos.
  useEffect(() => {
    if (tab !== 0) return;
    for (const item of history) {
      if (!item.seen) {
        updateHistoryItem(item.id, { seen: true });
        markSeen(item.id, userId).catch(console.error);
      }
    }
  }, [history, userId, tab]);

  // Cambiar de pestaña con las flechas (si no estás escribiendo).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (t.closest("input, textarea, select")) return;
      if (e.key === "ArrowRight") go(Math.min(TABS.length - 1, tab + 1));
      if (e.key === "ArrowLeft") go(Math.max(0, tab - 1));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [tab]);

  // Al cambiar el tamaño de la ventana, que la página siga encajada.
  useEffect(() => {
    const onResize = () => {
      const el = pager.current;
      if (el) el.scrollLeft = tab * el.clientWidth;
    };
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, [tab]);

  function go(i: number) {
    const el = pager.current;
    if (!el) return;
    el.scrollTo({ left: i * el.clientWidth, behavior: "smooth" });
    setTab(i);
  }

  function onScroll() {
    const el = pager.current;
    if (!el) return;
    const i = Math.round(el.scrollLeft / el.clientWidth);
    if (i !== tab) setTab(i);
  }

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

  const unseen = history.filter((h) => !h.seen).length;

  return (
    <div className="shell">
      <header className="topbar">
        <span className="wordmark">4u</span>
        <h1>{TABS[tab].label}</h1>
        <button
          className={`icon-button refresh${refreshing ? " spinning" : ""}`}
          title="Refrescar"
          disabled={refreshing}
          onClick={async () => {
            setRefreshing(true);
            try {
              await Promise.all([syncInbox(userId).then(refreshHistory), refreshOutgoing(), refreshContacts()]);
            } catch (e) {
              setStatus({ text: errorMessage(e), error: true });
            } finally {
              setRefreshing(false);
            }
          }}
        >
          <Icon name="refresh" size={20} />
        </button>
      </header>

      <div className="pager" ref={pager} onScroll={onScroll}>
        <div className="page">
          <InboxTab
            items={history}
            pinned={pinned}
            onReact={reactTo}
            onPin={(item) =>
              guard(async () => {
                setPinned(item?.id ?? null);
                setPinnedState(item?.id ?? null);
                await emit("widget:pin");
              }, item ? "Fijado en el widget" : "Quitado del widget")
            }
          />
        </div>
        <div className="page">
          <ComposeTab userId={userId} contacts={contacts} busy={busy} guard={guard} onSent={refreshOutgoing} />
        </div>
        <div className="page">
          <OutboxTab pending={outPending} sent={sent} localThumbs={localThumbs} busy={busy} onCancel={cancel} />
        </div>
        <div className="page">
          <ContactsTab
            userId={userId}
            contacts={contacts}
            busy={busy}
            onPing={ping}
            onChanged={refreshContacts}
            guard={guard}
          />
        </div>
        <div className="page">
          <AccountTab userId={userId} busy={busy} guard={guard} />
        </div>
      </div>

      {status && <div className={`toast${status.error ? " error" : ""}`}>{status.text}</div>}

      <nav className="tabbar">
        {TABS.map((t, i) => (
          <button key={t.key} className={i === tab ? "on" : ""} onClick={() => go(i)} aria-current={i === tab}>
            <span className="tab-icon">
              <Icon name={t.icon} size={22} />
              {i === 0 && unseen > 0 && <span className="dot" />}
              {i === 2 && outPending.length > 0 && <span className="count">{outPending.length}</span>}
            </span>
            {t.label}
          </button>
        ))}
      </nav>
    </div>
  );
}
