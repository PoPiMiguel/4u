// Formato de los dibujos (DECISIONS §12). Coordenadas x/y normalizadas a 0..1
// sobre el ancho y el alto del lienzo; `w`/`h` dan la proporción (1 × 1,5).
// `width` es el grosor relativo al ancho. `t` son ms desde el principio, con
// las pausas entre trazos recortadas para que la reproducción no se haga larga.

export interface Stroke {
  color: string;
  width: number;
  points: [number, number, number][];
}

export interface Drawing {
  w: number;
  h: number;
  bg: string;
  strokes: Stroke[];
}

export const ASPECT = 1.5;
export const MAX_GAP_MS = 350;
/** La reproducción dura como mucho esto (se acelera si hace falta). */
export const MAX_PLAY_MS = 6000;

export function duration(d: Drawing): number {
  let end = 0;
  for (const s of d.strokes) {
    const last = s.points[s.points.length - 1];
    if (last && last[2] > end) end = last[2];
  }
  return end;
}

/** Pinta el dibujo hasta el instante `t` (ms, en tiempo del dibujo; Infinity = entero). */
export function render(ctx: CanvasRenderingContext2D, d: Drawing, W: number, H: number, t = Infinity) {
  ctx.fillStyle = d.bg;
  ctx.fillRect(0, 0, W, H);
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  for (const s of d.strokes) {
    if (s.points.length === 0 || s.points[0][2] > t) break;
    ctx.strokeStyle = s.color;
    ctx.fillStyle = s.color;
    ctx.lineWidth = Math.max(1, s.width * W);
    const pts = s.points.filter((p) => p[2] <= t);
    if (pts.length === 1) {
      ctx.beginPath();
      ctx.arc(pts[0][0] * W, pts[0][1] * H, ctx.lineWidth / 2, 0, Math.PI * 2);
      ctx.fill();
      continue;
    }
    ctx.beginPath();
    ctx.moveTo(pts[0][0] * W, pts[0][1] * H);
    for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0] * W, pts[i][1] * H);
    ctx.stroke();
  }
}

/** Imagen final del dibujo (miniatura para el widget y el historial). */
export async function renderJpeg(d: Drawing, maxSide = 512): Promise<Blob> {
  const H = maxSide;
  const W = Math.round(maxSide / ASPECT);
  const canvas = document.createElement("canvas");
  canvas.width = W;
  canvas.height = H;
  render(canvas.getContext("2d")!, d, W, H);
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("No se pudo crear la imagen"))), "image/jpeg", 0.9),
  );
}

export function parseDrawing(text: string): Drawing {
  const d = JSON.parse(text) as Drawing;
  if (!d || !Array.isArray(d.strokes)) throw new Error("Dibujo no válido");
  return d;
}
