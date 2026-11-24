import { useEffect, useState } from "react";
import { sandboxHtml } from "../lib/sandbox";

/** Reproduce una animación HTML aislada (ver lib/sandbox.ts). */
export default function HtmlPlayer({ src }: { src: string }) {
  const [html, setHtml] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    fetch(src)
      .then((r) => r.text())
      .then((text) => alive && setHtml(sandboxHtml(text)))
      .catch((e) => console.error("No se pudo cargar la animación", e));
    return () => {
      alive = false;
    };
  }, [src]);

  if (html === null) return <div className="media emoji">…</div>;
  return <iframe className="media" sandbox="allow-scripts" srcDoc={html} title="animación" />;
}
