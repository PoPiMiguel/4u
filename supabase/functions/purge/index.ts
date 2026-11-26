// Limpieza diaria (pg_cron → private.call_function('purge')):
//  - Originales con más de 30 días: fuera de Storage y el mensaje pasa a
//    `purged` (se queda la miniatura, para el historial de otros dispositivos).
//  - Miniaturas con más de 180 días: fuera de Storage.
//  - Códigos de invitación caducados.
// Los metadatos se conservan. Ver DECISIONS §7.

import { admin, isAuthorized } from "../_shared/admin.ts";

Deno.serve(async (req) => {
  if (!isAuthorized(req)) return new Response("forbidden", { status: 403 });

  try {
    let purged = 0;
    // Por lotes de 200 hasta vaciar candidatos.
    while (true) {
      const { data, error } = await admin.rpc("purge_candidates", { p_limit: 200 });
      if (error) throw error;
      const rows = (data ?? []) as { id: string; storage_path: string | null; thumb_path: string | null }[];
      if (rows.length === 0) break;

      const paths = rows.map((r) => r.storage_path).filter((p): p is string => !!p);
      if (paths.length > 0) {
        const { error: rmError } = await admin.storage.from("media").remove(paths);
        if (rmError) throw rmError;
      }

      const { error: upError } = await admin
        .from("messages")
        .update({ status: "purged", storage_path: null })
        .in("id", rows.map((r) => r.id));
      if (upError) throw upError;

      purged += rows.length;
      if (rows.length < 200) break;
    }

    let thumbs = 0;
    while (true) {
      const { data, error } = await admin.rpc("purge_thumb_candidates", { p_limit: 200 });
      if (error) throw error;
      const rows = (data ?? []) as { id: string; thumb_path: string }[];
      if (rows.length === 0) break;

      const { error: rmError } = await admin.storage.from("media").remove(rows.map((r) => r.thumb_path));
      if (rmError) throw rmError;

      const { error: upError } = await admin
        .from("messages")
        .update({ thumb_path: null })
        .in("id", rows.map((r) => r.id));
      if (upError) throw upError;

      thumbs += rows.length;
      if (rows.length < 200) break;
    }

    const { error: invError } = await admin.from("invites").delete().lt("expires_at", new Date().toISOString());
    if (invError) throw invError;

    return Response.json({ purged, thumbs });
  } catch (e) {
    console.error(e);
    return new Response(String(e), { status: 500 });
  }
});
