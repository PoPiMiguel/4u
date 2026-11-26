import { convertFileSrc } from "@tauri-apps/api/core";
import type { HistoryItem } from "../lib/types";
import HtmlPlayer from "./HtmlPlayer";
import DrawingPlayer from "./DrawingPlayer";
import Icon from "./Icon";

/** Muestra un elemento recibido según su tipo. `compact` evita iframes en miniaturas. */
export default function MediaView({ item, compact = false }: { item: HistoryItem; compact?: boolean }) {
  if (item.type === "ping")
    return (
      <div className="media emoji">
        <Icon name="pulse" size={40} />
      </div>
    );
  // Original caducado en el servidor antes de llegar a este dispositivo: la miniatura.
  if (!item.localPath && item.thumbPath) return <img className="media" src={convertFileSrc(item.thumbPath)} alt="" />;
  if (!item.localPath) return <div className="media emoji">…</div>;

  const src = convertFileSrc(item.localPath);
  switch (item.type) {
    case "photo":
    case "gif":
      return <img className="media" src={src} alt="" />;
    case "video":
      return <video className="media" src={src} autoPlay loop muted playsInline />;
    case "html":
      return compact ? <div className="media emoji">HTML</div> : <HtmlPlayer src={src} />;
    case "drawing":
      return <DrawingPlayer src={src} still={compact} />;
    default:
      return null;
  }
}
