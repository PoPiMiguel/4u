import { useEffect, useRef, useState } from "react";
import { ASPECT, MAX_GAP_MS, render, type Drawing, type Stroke } from "../lib/drawing";

const COLORS = ["#ffffff", "#e84a6f", "#ff9f43", "#feca57", "#1dd1a1", "#54a0ff", "#a55eea", "#111111"];
const WIDTHS = [0.008, 0.018, 0.04];
const BACKGROUNDS = ["#121216", "#fdf6ec", "#ffd6e0"];

/** Lienzo para dibujar. Devuelve el dibujo con sus tiempos para reproducirlo. */
export default function DrawPad({ onDone, onCancel }: { onDone: (d: Drawing) => void; onCancel: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [strokes, setStrokes] = useState<Stroke[]>([]);
  const [color, setColor] = useState(COLORS[1]);
  const [width, setWidth] = useState(WIDTHS[1]);
  const [bg, setBg] = useState(BACKGROUNDS[0]);
  const current = useRef<Stroke | null>(null);
  // Reloj del dibujo: avanza mientras se dibuja; las pausas largas se recortan.
  const clock = useRef({ offset: 0, lastEnd: 0, strokeStart: 0 });

  const drawing = (): Drawing => ({ w: 1, h: ASPECT, bg, strokes });

  function paint(extra?: Stroke) {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d")!;
    render(ctx, { w: 1, h: ASPECT, bg, strokes: extra ? [...strokes, extra] : strokes }, canvas.width, canvas.height);
  }

  useEffect(() => {
    const canvas = canvasRef.current!;
    const ratio = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = Math.round(rect.width * ratio);
    canvas.height = Math.round(rect.height * ratio);
    paint();
  }, []);

  useEffect(() => paint(), [strokes, bg]);

  function pos(e: React.PointerEvent<HTMLCanvasElement>): [number, number] {
    const r = e.currentTarget.getBoundingClientRect();
    const x = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    const y = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height));
    return [x, y];
  }

  function now(): number {
    return performance.now() - clock.current.strokeStart + clock.current.offset;
  }

  function down(e: React.PointerEvent<HTMLCanvasElement>) {
    e.currentTarget.setPointerCapture(e.pointerId);
    const c = clock.current;
    const t = performance.now();
    // El trazo empieza poco después del anterior, aunque hayas tardado más.
    const gap = strokes.length === 0 ? 0 : Math.min(MAX_GAP_MS, t - c.lastEnd);
    c.offset = (strokes.length === 0 ? 0 : lastTime(strokes)) + gap;
    c.strokeStart = t;
    const [x, y] = pos(e);
    current.current = { color, width, points: [[x, y, Math.round(c.offset)]] };
    paint(current.current);
  }

  function move(e: React.PointerEvent<HTMLCanvasElement>) {
    const s = current.current;
    if (!s) return;
    const [x, y] = pos(e);
    const last = s.points[s.points.length - 1];
    if (Math.hypot(x - last[0], y - last[1]) < 0.002) return;
    s.points.push([round(x), round(y), Math.round(now())]);
    paint(s);
  }

  function up() {
    const s = current.current;
    if (!s) return;
    current.current = null;
    clock.current.lastEnd = performance.now();
    setStrokes((prev) => [...prev, s]);
  }

  return (
    <div className="drawpad">
      <canvas
        ref={canvasRef}
        className="drawpad-canvas"
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={up}
      />
      <div className="row">
        {COLORS.map((c) => (
          <button
            key={c}
            className={`swatch${c === color ? " on" : ""}`}
            style={{ background: c }}
            onClick={() => setColor(c)}
            aria-label={`Color ${c}`}
          />
        ))}
      </div>
      <div className="row">
        {WIDTHS.map((w, i) => (
          <button key={w} className={`chip${w === width ? " on" : ""}`} onClick={() => setWidth(w)}>
            {["Fino", "Medio", "Grueso"][i]}
          </button>
        ))}
        <span className="muted">Fondo</span>
        {BACKGROUNDS.map((b) => (
          <button
            key={b}
            className={`swatch${b === bg ? " on" : ""}`}
            style={{ background: b }}
            onClick={() => setBg(b)}
            aria-label={`Fondo ${b}`}
          />
        ))}
      </div>
      <div className="row">
        <button className="chip" disabled={strokes.length === 0} onClick={() => setStrokes((s) => s.slice(0, -1))}>
          Deshacer
        </button>
        <button className="chip" disabled={strokes.length === 0} onClick={() => setStrokes([])}>
          Borrar todo
        </button>
        <span style={{ flex: 1 }} />
        <button className="chip" onClick={onCancel}>
          Cancelar
        </button>
        <button disabled={strokes.length === 0} onClick={() => onDone(drawing())}>
          Usar dibujo
        </button>
      </div>
    </div>
  );
}

function lastTime(strokes: Stroke[]): number {
  const s = strokes[strokes.length - 1];
  return s.points[s.points.length - 1][2];
}

function round(v: number): number {
  return Math.round(v * 10000) / 10000;
}
