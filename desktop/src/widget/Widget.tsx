import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { loadHistory } from "../lib/store";
import type { HistoryItem, WidgetSize } from "../lib/types";
import MediaView from "../components/MediaView";

function initialSize(): WidgetSize {
  const s = localStorage.getItem("4u.widgetSize");
  return s === "medium" || s === "large" ? s : "small";
}

export default function Widget() {
  const [items, setItems] = useState<HistoryItem[]>(loadHistory);
  const [size, setSize] = useState<WidgetSize>(initialSize);
  const [shake, setShake] = useState(false);

  useEffect(() => {
    invoke("set_widget_size", { size }).catch(console.error);
  }, [size]);

  useEffect(() => {
    const reload = () => setItems(loadHistory());
    const unNew = listen<HistoryItem>("inbox:new", (e) => {
      reload();
      if (e.payload.type === "ping") {
        setShake(true);
        setTimeout(() => setShake(false), 1200);
      }
    });
    const unSize = listen<WidgetSize>("widget:size", (e) => setSize(e.payload));
    window.addEventListener("storage", reload);
    return () => {
      unNew.then((f) => f());
      unSize.then((f) => f());
      window.removeEventListener("storage", reload);
    };
  }, []);

  const latest = items[0];

  return (
    <div className={`widget ${size}${shake ? " shake" : ""}`} onDoubleClick={() => invoke("show_main")}>
      <div className="drag" data-tauri-drag-region />
      {latest ? (
        <>
          <div className="widget-main">
            <MediaView item={latest} />
            {latest.caption && <span className="widget-caption">{latest.caption}</span>}
          </div>
          {size !== "small" && (
            <div className="widget-meta">
              <strong>{latest.senderName}</strong>
              <span>{new Date(latest.receivedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
            </div>
          )}
          {size === "large" && (
            <div className="widget-strip">
              {items.slice(1, 4).map((i) => (
                <div key={i.id} className="widget-thumb">
                  <MediaView item={i} compact />
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="widget-empty">4u 💌</div>
      )}
    </div>
  );
}
