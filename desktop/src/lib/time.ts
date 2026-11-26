const rtf = new Intl.RelativeTimeFormat("es", { numeric: "auto" });

/** "hace 5 minutos", "ayer"…; a partir de una semana, la fecha. */
export function relativeTime(iso: string): string {
  const diff = (new Date(iso).getTime() - Date.now()) / 1000;
  const abs = Math.abs(diff);
  if (abs < 60) return "ahora";
  if (abs < 3600) return rtf.format(Math.round(diff / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), "hour");
  if (abs < 7 * 86400) return rtf.format(Math.round(diff / 86400), "day");
  return new Date(iso).toLocaleDateString([], { day: "numeric", month: "short" });
}

export function dateTime(iso: string): string {
  return new Date(iso).toLocaleString([], { dateStyle: "short", timeStyle: "short" });
}
