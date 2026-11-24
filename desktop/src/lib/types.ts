export type MessageType = "photo" | "video" | "gif" | "html" | "drawing" | "ping";

export type WidgetSize = "small" | "medium" | "large";

export interface Contact {
  contactId: string;
  name: string;
  nickname: string | null;
  status: "accepted" | "blocked";
}

/** Elemento recibido, guardado en local. Lo leen la ventana principal y el widget. */
export interface HistoryItem {
  id: string;
  type: MessageType;
  senderId: string;
  senderName: string;
  caption: string | null;
  localPath: string | null;
  receivedAt: string;
  seen: boolean;
  reaction: string | null;
}
