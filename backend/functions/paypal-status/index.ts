const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

async function getSupabaseUser(req: Request) {
  const auth = req.headers.get("Authorization");
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const anon = Deno.env.get("SUPABASE_ANON_KEY")?.trim();
  if (!auth || !supabaseUrl || !anon) return null;
  const r = await fetch(`${supabaseUrl}/auth/v1/user`, { headers: { apikey: anon, Authorization: auth } });
  if (!r.ok) return null;
  return await r.json();
}

async function paypalAccessToken(baseUrl: string, clientId: string, secret: string) {
  const r = await fetch(`${baseUrl}/v1/oauth2/token`, {
    method: "POST",
    headers: {
      Authorization: `Basic ${btoa(`${clientId}:${secret}`)}`,
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
    },
    body: "grant_type=client_credentials",
  });
  const data = await r.json();
  if (!r.ok || typeof data.access_token !== "string") throw new Error("PayPal authentication failed");
  return data.access_token as string;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return response({ error: "Method not allowed" }, 405);

  try {
    const user = await getSupabaseUser(req);
    if (!user?.id) return response({ error: "You must be signed in." }, 401);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
    const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
    const clientId = Deno.env.get("PAYPAL_CLIENT_ID")?.trim();
    const secret = Deno.env.get("PAYPAL_CLIENT_SECRET")?.trim();
    const baseUrl = (Deno.env.get("PAYPAL_BASE_URL") || "https://api-m.paypal.com").trim().replace(/\/$/, "");
    if (!supabaseUrl || !serviceRole || !clientId || !secret) return response({ error: "PayPal billing is not configured yet." }, 503);

    const db = await fetch(`${supabaseUrl}/rest/v1/billing_subscriptions?user_id=eq.${encodeURIComponent(user.id)}&select=paypal_subscription_id,status,updated_at&limit=1`, {
      headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` },
    });
    if (!db.ok) return response({ error: "Unable to read subscription status." }, 502);
    const rows = await db.json();
    if (!Array.isArray(rows) || rows.length === 0) return response({ active: false, status: "NONE" });

    const record = rows[0];
    const token = await paypalAccessToken(baseUrl, clientId, secret);
    const paypal = await fetch(`${baseUrl}/v1/billing/subscriptions/${encodeURIComponent(record.paypal_subscription_id)}`, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
    });
    const data = await paypal.json();
    if (!paypal.ok) return response({ active: record.status === "ACTIVE", status: record.status });

    const status = typeof data.status === "string" ? data.status : record.status;
    const active = status === "ACTIVE" || status === "APPROVAL_PENDING";

    await fetch(`${supabaseUrl}/rest/v1/billing_subscriptions?user_id=eq.${encodeURIComponent(user.id)}`, {
      method: "PATCH",
      headers: {
        apikey: serviceRole,
        Authorization: `Bearer ${serviceRole}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify({ status, updated_at: new Date().toISOString() }),
    });

    return response({ active: status === "ACTIVE", status, subscription_id: record.paypal_subscription_id });
  } catch (error) {
    console.error("paypal-status", error instanceof Error ? error.message : "unknown error");
    return response({ error: "Unable to check PayPal status right now." }, 502);
  }
});
