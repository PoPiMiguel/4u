// Envío de push por FCM HTTP v1 usando la cuenta de servicio de Firebase.
// Secreto requerido: FCM_SERVICE_ACCOUNT (JSON completo de la cuenta de servicio).

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

export type FcmResult = "ok" | "unregistered" | "error";

let serviceAccount: ServiceAccount | null = null;
let cachedToken: { token: string; exp: number } | null = null;

function getServiceAccount(): ServiceAccount {
  if (!serviceAccount) {
    const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
    if (!raw) throw new Error("Falta el secreto FCM_SERVICE_ACCOUNT");
    serviceAccount = JSON.parse(raw) as ServiceAccount;
  }
  return serviceAccount;
}

function base64url(input: Uint8Array | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

async function getAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp - 60 > now) return cachedToken.token;

  const sa = getServiceAccount();
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const key = await importPrivateKey(sa.private_key);
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  const jwt = `${header}.${claims}.${base64url(signature)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`OAuth Google ${res.status}: ${await res.text()}`);
  const json = await res.json();
  cachedToken = { token: json.access_token, exp: now + Number(json.expires_in ?? 3600) };
  return cachedToken.token;
}

/** Envía un mensaje de datos (sin notificación automática) de alta prioridad. */
export async function sendFcm(token: string, data: Record<string, string>): Promise<FcmResult> {
  const sa = getServiceAccount();
  const accessToken = await getAccessToken();
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: { token, data, android: { priority: "high" } },
    }),
  });
  if (res.ok) return "ok";
  const text = await res.text();
  if (res.status === 404 || text.includes("UNREGISTERED")) return "unregistered";
  console.error("FCM error", res.status, text);
  return "error";
}
