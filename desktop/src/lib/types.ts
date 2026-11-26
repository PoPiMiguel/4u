export type MessageType = "photo" | "video" | "gif" | "html" | "drawing" | "ping";

export type WidgetSize = "small" | "medium" | "large";

export interface Contact {
  contactId: string;
  /** Apodo si lo hay; si no, su nombre. */
  name: string;
  displayName: string;
  nickname: string | null;
  status: "accepted" | "blocked";
  avatarUrl: string | null;
}

/** Elemento recibido, guardado en local. Lo leen la ventana principal y el widget. */
export interface HistoryItem {
  id: string;
  type: MessageType;
  senderId: string;
  senderName: string;
  caption: string | null;
  localPath: string | null;
  /** Miniatura en local cuando el original ya caducó en el servidor. */
  thumbPath?: string | null;
  expired?: boolean;
  /** Cuándo se envió (orden del historial). */
  receivedAt: string;
  seen: boolean;
  reaction: string | null;
}
