import type { HistoryItem } from "../lib/types";
import MediaView from "../components/MediaView";
import Icon from "../components/Icon";
import { relativeTime } from "../lib/time";

const REACTIONS = ["❤️", "😂", "😮", "😢", "🔥"];

/** Lo recibido, como un muro de tarjetas. */
interface Props {
  items: HistoryItem[];
  pinned: string | null;
  onReact: (i: HistoryItem, r: string) => void;
  onPin: (i: HistoryItem | null) => void;
}

export default function InboxTab({ items, pinned, onReact, onPin }: Props) {
  if (items.length === 0) {
    return (
      <div className="empty">
        <Icon name="inbox" size={36} />
        <p>Cuando te envíen algo, aparecerá aquí.</p>
      </div>
    );
  }

  return (
    <div className="feed">
      {items.map((item) =>
        item.type === "ping" ? (
          <article key={item.id} className="ping-card">
            <Icon name="pulse" size={22} />
            <span>
              <strong>{item.senderName}</strong> está pensando en ti
            </span>
            <time>{relativeTime(item.receivedAt)}</time>
          </article>
        ) : (
          <article key={item.id} className="post">
            <header>
              <strong>{item.senderName}</strong>
              <time>{relativeTime(item.receivedAt)}</time>
              <button
                className={`icon-button pin${pinned === item.id ? " on" : ""}`}
                onClick={() => onPin(pinned === item.id ? null : item)}
                title={pinned === item.id ? "Quitar del widget" : "Fijar en el widget"}
              >
                <Icon name="pin" size={18} />
              </button>
            </header>
            <div className="post-media">
              <MediaView item={item} />
            </div>
            {item.caption && <p className="post-caption">{item.caption}</p>}
            <div className="reactions">
              {REACTIONS.map((r) => (
                <button
                  key={r}
                  className={`reaction${item.reaction === r ? " on" : ""}`}
                  onClick={() => onReact(item, r)}
                  aria-pressed={item.reaction === r}
                >
                  {r}
                </button>
              ))}
            </div>
          </article>
        ),
      )}
    </div>
  );
}
