const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const LEARN_ANYTHING_TEACHING_CONTRACT = `
You are the teaching engine for Learn Anything. Do not behave like a search box that merely defines things or dumps information. Help the learner genuinely understand a subject and, when the goal is a skill, become able to perform it.

UNIVERSAL TEACHING RULES:
1. Identify the learner's actual end goal: what they want to understand, make, solve, perform, build, decide, or accomplish.
2. Start with only the necessary foundations and prerequisites. Do not assume knowledge the learner has not demonstrated.
3. Build from fundamentals to the specific goal in a logical order. Avoid filler lessons and unnecessary theory.
4. Teach before testing. Never turn a lesson into a quiz with a tiny explanation attached.
5. For practical or procedural goals, provide an actionable walkthrough: preparation, required tools/materials when relevant, each step in order, what the learner should see/do, why the step matters, checks for correctness, and what to do if something does not look right.
6. For conceptual goals, use plain-language explanations, analogies, visual mental models, worked examples, and progressively harder practice.
7. For physical skills, describe useful diagrams, labeled visuals, animations, demonstrations, or other visual instruction whenever the product supports them. Never pretend a visual was generated if it was not.
8. For math/science/technical problems, work through representative examples step by step and explain the reasoning behind each step.
9. For creative skills, demonstrate the process, give a small guided exercise, then let the learner make their own version and receive feedback.
10. For a specific model, version, tool, recipe, software release, or other variant, use the exact variant when known. If a critical detail changes the procedure and is unknown, ask for it instead of guessing.
11. Never invent measurements, torque values, compatibility claims, safety requirements, or other model-specific facts. Prefer manufacturer documentation or clearly label information that must be verified.
12. Include useful memory tricks, shortcuts, patterns, checklists, and best practices when they genuinely improve learning.
13. Give the learner a chance to try the skill after seeing the demonstration. Then check their result and explain mistakes constructively.
14. If the learner says they do not understand, change the teaching approach instead of repeating the same explanation.
15. End lessons with a concise recap and a realistic next step or application.
16. Adapt difficulty from demonstrated knowledge and performance.
17. For potentially hazardous physical activities, prioritize safety and recommend qualified adult/professional help when appropriate.
18. Be transparent about what is and is not actually available.

OUTPUT STYLE:
- Be clear, concrete, encouraging, and concise without being shallow.
- Prefer headings, numbered steps, checklists, worked examples, and short practice tasks over giant paragraphs.
- When appropriate, explicitly label WHY IT MATTERS, WHAT YOU NEED, WATCH/SHOW, DO THIS, CHECK YOUR WORK, COMMON MISTAKE, and TRY IT.
- The learner should leave each lesson knowing both what to do and why it works.
`;

function errorResponse(status: number, error: string) {
  return new Response(JSON.stringify({ error }), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

function convertMessages(messages: Array<{ role?: string; content?: unknown }>) {
  const systemParts: string[] = [LEARN_ANYTHING_TEACHING_CONTRACT];
  const contents: Array<{ role: "user" | "model"; parts: Array<{ text: string }> }> = [];

  for (const message of messages) {
    const role = message?.role;
    const text = typeof message?.content === "string" ? message.content : "";
    if (!text) continue;

    if (role === "system") {
      systemParts.push(text);
    } else if (role === "user" || role === "assistant") {
      contents.push({
        role: role === "assistant" ? "model" : "user",
        parts: [{ text }],
      });
    }
  }

  return { systemInstruction: systemParts.join("\n\n"), contents };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return errorResponse(405, "Method not allowed");

  const apiKey = Deno.env.get("GEMINI_API_KEY")?.trim();
  if (!apiKey) return errorResponse(503, "AI service is not configured");

  let body: unknown;
  try {
    const raw = await req.text();
    if (raw.length > 100_000) return errorResponse(413, "Request too large");
    body = JSON.parse(raw);
  } catch {
    return errorResponse(400, "Invalid JSON");
  }

  if (typeof body !== "object" || body === null) return errorResponse(400, "Invalid request");

  const request = body as { messages?: unknown; max_tokens?: unknown; temperature?: unknown };
  if (!Array.isArray(request.messages)) return errorResponse(400, "messages must be an array");

  const { systemInstruction, contents } = convertMessages(
    request.messages as Array<{ role?: string; content?: unknown }>,
  );

  if (contents.length === 0) return errorResponse(400, "No user messages supplied");

  const maxOutputTokens = typeof request.max_tokens === "number"
    ? Math.max(1, Math.min(Math.floor(request.max_tokens), 8192))
    : 1200;
  const temperature = typeof request.temperature === "number"
    ? Math.max(0, Math.min(request.temperature, 2))
    : 0.6;

  const payload = {
    systemInstruction: { parts: [{ text: systemInstruction }] },
    contents,
    generationConfig: {
      maxOutputTokens,
      temperature,
    },
  };

  try {
    const upstream = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
      {
        method: "POST",
        headers: {
          "x-goog-api-key": apiKey,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(90_000),
      },
    );

    const raw = await upstream.text();
    if (!upstream.ok) {
      console.error("Gemini request failed", upstream.status, raw.slice(0, 1000));
      return new Response(raw, {
        status: upstream.status,
        headers: { ...cors, "Content-Type": upstream.headers.get("Content-Type") ?? "application/json" },
      });
    }

    let data: any;
    try {
      data = JSON.parse(raw);
    } catch {
      return errorResponse(502, "Invalid response from AI service");
    }

    const text = data?.candidates?.[0]?.content?.parts
      ?.map((part: any) => typeof part?.text === "string" ? part.text : "")
      .join("")
      .trim();

    if (!text) {
      console.error("Gemini returned no text", JSON.stringify(data).slice(0, 2000));
      return errorResponse(502, "AI service returned an empty response");
    }

    // Keep the response shape expected by the existing Android client.
    return new Response(JSON.stringify({
      choices: [{
        index: 0,
        message: { role: "assistant", content: text },
        finish_reason: data?.candidates?.[0]?.finishReason ?? "STOP",
      }],
    }), {
      status: 200,
      headers: { ...cors, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("Gemini upstream request failed", error instanceof Error ? error.message : "unknown error");
    return errorResponse(502, "AI service temporarily unavailable");
  }
});
