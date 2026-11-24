import type { MessageType } from "./types";

const MB = 1024 * 1024;

export const LIMITS = {
  html: 2 * MB,
  gif: 10 * MB,
  video: 15 * MB,
  photoMaxSide: 2048,
  thumbMaxSide: 512,
};

export interface PreparedFile {
  type: MessageType;
  file: Blob;
  ext: string;
  mime: string;
  thumb?: Blob;
}

/** Valida y prepara un archivo para enviar (redimensiona fotos, genera miniaturas). */
export async function prepareFile(f: File): Promise<PreparedFile> {
  const name = f.name.toLowerCase();

  if (name.endsWith(".html") || name.endsWith(".htm")) {
    if (f.size > LIMITS.html) throw new Error("La animación HTML no puede superar 2 MB");
    return { type: "html", file: f, ext: "html", mime: "text/html" };
  }

  if (f.type === "image/gif") {
    if (f.size > LIMITS.gif) throw new Error("El GIF no puede superar 10 MB");
    const thumb = await resizeImage(f, LIMITS.thumbMaxSide).catch(() => undefined);
    return { type: "gif", file: f, ext: "gif", mime: "image/gif", thumb };
  }

  if (f.type.startsWith("image/")) {
    const [full, thumb] = await Promise.all([
      resizeImage(f, LIMITS.photoMaxSide),
      resizeImage(f, LIMITS.thumbMaxSide),
    ]);
    return { type: "photo", file: full, ext: "jpg", mime: "image/jpeg", thumb };
  }

  if (f.type.startsWith("video/")) {
    // TODO(M3): comprimir con ffmpeg (sidecar o el del sistema en Linux).
    if (f.size > LIMITS.video) throw new Error("El vídeo no puede superar 15 MB (compresión automática pendiente)");
    const ext = name.split(".").pop() || "mp4";
    const thumb = await videoThumbnail(f).catch(() => undefined);
    return { type: "video", file: f, ext, mime: f.type, thumb };
  }

  throw new Error("Tipo de archivo no soportado");
}

async function canvasToJpeg(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("No se pudo convertir la imagen"))), "image/jpeg", 0.85),
  );
}

export async function resizeImage(source: Blob, maxSide: number): Promise<Blob> {
  const bmp = await createImageBitmap(source);
  const scale = Math.min(1, maxSide / Math.max(bmp.width, bmp.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(bmp.width * scale));
  canvas.height = Math.max(1, Math.round(bmp.height * scale));
  canvas.getContext("2d")!.drawImage(bmp, 0, 0, canvas.width, canvas.height);
  bmp.close();
  return canvasToJpeg(canvas);
}

async function videoThumbnail(file: Blob): Promise<Blob> {
  const url = URL.createObjectURL(file);
  try {
    const video = document.createElement("video");
    video.muted = true;
    video.preload = "metadata";
    video.src = url;
    await new Promise<void>((resolve, reject) => {
      video.onloadeddata = () => resolve();
      video.onerror = () => reject(new Error("No se pudo leer el vídeo"));
    });
    video.currentTime = Math.min(0.5, video.duration / 2 || 0);
    await new Promise<void>((resolve) => (video.onseeked = () => resolve()));
    const scale = Math.min(1, LIMITS.thumbMaxSide / Math.max(video.videoWidth, video.videoHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    canvas.getContext("2d")!.drawImage(video, 0, 0, canvas.width, canvas.height);
    return await canvasToJpeg(canvas);
  } finally {
    URL.revokeObjectURL(url);
  }
}
