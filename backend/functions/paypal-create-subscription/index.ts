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

  const r = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { apikey: anon, Authorization: auth },
  });
  if (!r.ok) return null;
  return await r.json();
}

async function paypalAccessToken(baseUrl: string, clientId: string, secret: string) {
  const basic = btoa(`${clientId}:${secret}`);
  const r = await fetch(`${baseUrl}/v1/oauth2/token`, {
    method: "POST",
    headers: {
      Authorization: `Basic ${basic}`,
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
    },
    body: "grant_type=client_credentials",
  });
  const data = await r.json();
  if (!r.ok || typeof data.access_token !== "string") {
    console.error("PayPal OAuth failed", r.status, JSON.stringify(data).slice(0, 1000));
    throw new Error("PayPal authentication failed");
  }
  return data.access_token as string;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return response({ error: "Method not allowed" }, 405);

  try {
    const user = await getSupabaseUser(req);
    if (!user?.id) return response({ error: "You must be signed in." }, 401);

    const clientId = Deno.env.get("PAYPAL_CLIENT_ID")?.trim();
    const secret = Deno.env.get("PAYPAL_CLIENT_SECRET")?.trim();
    const planId = Deno.env.get("PAYPAL_PLAN_ID")?.trim();
    const baseUrl = (Deno.env.get("PAYPAL_BASE_URL") || "https://api-m.paypal.com").trim().replace(/\/$/, "");
    const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
    const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();

    if (!clientId || !secret || !planId || !supabaseUrl || !serviceRole) {
      return response({ error: "PayPal billing is not configured yet." }, 503);
    }

    const accessToken = await paypalAccessToken(baseUrl, clientId, secret);
    const appUrl = Deno.env.get("PAYPAL_RETURN_URL")?.trim() || "https://learnanything.app/paypal-return";
    const cancelUrl = Deno.env.get("PAYPAL_CANCEL_URL")?.trim() || "https://learnanything.app/paypal-cancel";

    const paypal = await fetch(`${baseUrl}/v1/billing/subscriptions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
        Accept: "application/json",
        Prefer: "return=representation",
      },
      body: JSON.stringify({
        plan_id: planId,
        subscriber: user.email ? { email_address: user.email } : undefined,
        application_context: {
          brand_name: "Learn Anything",
          locale: "en-US",
          shipping_preference: "NO_SHIPPING",
          user_action: "SUBSCRIBE_NOW",
          payment_method: { payer_selected: "PAYPAL", payee_preferred: "IMMEDIATE_PAYMENT_REQUIRED" },
          return_url: appUrl,
          cancel_url: cancelUrl,
        },
      }),
    });

    const data = await paypal.json();
    if (!paypal.ok || typeof data.id !== "string") {
      console.error("PayPal subscription creation failed", paypal.status, JSON.stringify(data).slice(0, 2000));
      return response({ error: "PayPal could not create the subscription." }, 502);
    }

    const approval = Array.isArray(data.links)
      ? data.links.find((link: any) => link?.rel === "approve")?.href
      : null;
    if (typeof approval !== "string") return response({ error: "PayPal did not return an approval link." }, 502);

    const db = await fetch(`${supabaseUrl}/rest/v1/billing_subscriptions`, {
      method: "POST",
      headers: {
        apikey: serviceRole,
        Authorization: `Bearer ${serviceRole}`,
        "Content-Type": "application/json",
        Prefer: "resolution=merge-duplicates,return=minimal",
      },
      body: JSON.stringify({
        user_id: user.id,
        paypal_subscription_id: data.id,
        paypal_plan_id: planId,
        status: data.status || "APPROVAL_PENDING",
      }),
    });
    if (!db.ok) console.error("Failed to save subscription mapping", db.status, (await db.text()).slice(0, 1000));

    return response({ subscription_id: data.id, status: data.status, approve_url: approval });
  } catch (error) {
    console.error("paypal-create-subscription", error instanceof Error ? error.message : "unknown error");
    return response({ error: "Unable to start PayPal checkout right now." }, 502);
  }
});
