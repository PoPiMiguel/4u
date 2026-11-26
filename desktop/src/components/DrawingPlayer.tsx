import { useEffect, useRef, useState } from "react";
import { ASPECT, MAX_PLAY_MS, duration, parseDrawing, render, type Drawing } from "../lib/drawing";

/**
 * Reproduce un dibujo "dibujándose" y se queda en la imagen final.
 * Clic para volver a verlo. `still` lo pinta terminado, sin animación.
 */
export default function DrawingPlayer({ src, still = false }: { src: string; still?: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [drawing, setDrawing] = useState<Drawing | null>(null);
  const [run, setRun] = useState(0);

  useEffect(() => {
    let alive = true;
    fetch(src)
      .then((r) => r.text())
      .then((t) => alive && setDrawing(parseDrawing(t)))
      .catch((e) => console.error("No se pudo cargar el dibujo", e));
    return () => {
      alive = false;
    };
  }, [src]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !drawing) return;
    const ratio = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = Math.max(1, Math.round(rect.width * ratio));
    canvas.height = Math.max(1, Math.round(rect.height * ratio));
    const ctx = canvas.getContext("2d")!;

    const total = duration(drawing);
    if (still || total === 0) {
      render(ctx, drawing, canvas.width, canvas.height);
      return;
    }
    const speed = Math.max(1, total / MAX_PLAY_MS);
    const start = performance.now();
    let frame = 0;
    const tick = () => {
      const t = (performance.now() - start) * speed;
      render(ctx, drawing, canvas.width, canvas.height, t);
      if (t < total) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [drawing, still, run]);

  if (!drawing) return <div className="media emoji">…</div>;
  return (
    <div className="drawing-frame" onClick={() => setRun((r) => r + 1)} title="Volver a ver">
      <canvas ref={canvasRef} className="drawing-canvas" style={{ aspectRatio: `1 / ${ASPECT}` }} />
    </div>
  );
}
