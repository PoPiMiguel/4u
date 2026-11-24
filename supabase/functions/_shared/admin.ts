import { createClient } from "jsr:@supabase/supabase-js@2";

// Cliente con service_role (salta RLS). SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY
// los inyecta Supabase automáticamente en las Edge Functions.
export const admin = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);

/** Las funciones solo aceptan llamadas de la base de datos (pg_net) con el secreto compartido. */
export function isAuthorized(req: Request): boolean {
  const expected = Deno.env.get("FUNCTIONS_SECRET");
  return !!expected && req.headers.get("x-4u-secret") === expected;
}
