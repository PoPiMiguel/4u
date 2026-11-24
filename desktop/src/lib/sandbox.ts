// Las animaciones HTML son código de terceros: se ejecutan en un iframe con
// sandbox="allow-scripts" (origen opaco: sin acceso a la app ni a la API de Tauri)
// y con esta CSP inyectada (sin red: solo inline y data:/blob:).

const CSP =
  "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
  "img-src data: blob:; media-src data: blob:; font-src data:";

/** Inserta la CSP justo después del doctype (o al principio) para que vaya en <head>. */
export function sandboxHtml(source: string): string {
  const meta = `<meta http-equiv="Content-Security-Policy" content="${CSP}">`;
  const doctype = source.match(/^\s*<!doctype[^>]*>/i);
  return doctype ? doctype[0] + meta + source.slice(doctype[0].length) : meta + source;
}
