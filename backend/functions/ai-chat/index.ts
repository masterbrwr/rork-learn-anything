const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405, headers: { ...cors, "Content-Type": "application/json" },
    });
  }

  const gatewayUrl = Deno.env.get("AI_GATEWAY_URL")?.trim().replace(/\/$/, "");
  const gatewaySecret = Deno.env.get("RORK_TOOLKIT_SECRET_KEY")?.trim();
  if (!gatewayUrl || !gatewaySecret) {
    return new Response(JSON.stringify({ error: "AI service is not configured" }), {
      status: 503, headers: { ...cors, "Content-Type": "application/json" },
    });
  }

  let body: unknown;
  try {
    const raw = await req.text();
    if (raw.length > 100_000) {
      return new Response(JSON.stringify({ error: "Request too large" }), {
        status: 413, headers: { ...cors, "Content-Type": "application/json" },
      });
    }
    body = JSON.parse(raw);
  } catch {
    return new Response(JSON.stringify({ error: "Invalid JSON" }), {
      status: 400, headers: { ...cors, "Content-Type": "application/json" },
    });
  }

  try {
    const upstream = await fetch(`${gatewayUrl}/v2/vercel/v1/chat/completions`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${gatewaySecret}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(90_000),
    });
    const responseBody = await upstream.text();
    return new Response(responseBody, {
      status: upstream.status,
      headers: { ...cors, "Content-Type": upstream.headers.get("Content-Type") ?? "application/json" },
    });
  } catch (error) {
    console.error("ai-chat upstream request failed", error instanceof Error ? error.message : "unknown error");
    return new Response(JSON.stringify({ error: "AI service temporarily unavailable" }), {
      status: 502, headers: { ...cors, "Content-Type": "application/json" },
    });
  }
});
