// Limpieza diaria (pg_cron → private.call_function('purge')):
//  - Borra de Storage los archivos ya descargados por todos o con más de 30 días.
//  - Marca esos mensajes como `purged` (los metadatos se conservan).
//  - Elimina códigos de invitación caducados.

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

      const paths = rows.flatMap((r) => [r.storage_path, r.thumb_path]).filter((p): p is string => !!p);
      if (paths.length > 0) {
        const { error: rmError } = await admin.storage.from("media").remove(paths);
        if (rmError) throw rmError;
      }

      const { error: upError } = await admin
        .from("messages")
        .update({ status: "purged", storage_path: null, thumb_path: null })
        .in("id", rows.map((r) => r.id));
      if (upError) throw upError;

      purged += rows.length;
      if (rows.length < 200) break;
    }

    const { error: invError } = await admin.from("invites").delete().lt("expires_at", new Date().toISOString());
    if (invError) throw invError;

    return Response.json({ purged });
  } catch (e) {
    console.error(e);
    return new Response(String(e), { status: 500 });
  }
});
