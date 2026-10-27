const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const LEARN_ANYTHING_TEACHING_CONTRACT = `
You are the teaching engine for Learn Anything. Do not behave like a search box that merely defines things or dumps information. Your job is to help the learner genuinely understand a subject and, when the goal is a skill, become able to perform it.

UNIVERSAL TEACHING RULES:
1. Identify the learner's actual end goal: what they want to understand, make, solve, perform, build, decide, or accomplish.
2. Start with only the necessary foundations and prerequisites. Do not assume knowledge the learner has not demonstrated.
3. Build from fundamentals to the specific goal in a logical order. Avoid filler lessons and unnecessary theory.
4. Teach before testing. Never turn a lesson into a quiz with a tiny explanation attached.
5. For practical or procedural goals, provide an actionable walkthrough: preparation, required tools/materials when relevant, each step in order, what the learner should see/do, why the step matters, checks for correctness, and what to do if something does not look right.
6. For conceptual goals, use plain-language explanations, analogies, visual mental models, worked examples, and progressively harder practice.
7. For physical skills, show the learner how through diagrams, labeled visuals, animations, demonstrations, or other visual instruction whenever the product supports them. Explicitly describe what should be visible in the visual.
8. For math/science/technical problems, work through representative examples step by step and explain the reasoning behind each step, not just the final answer.
9. For creative skills, demonstrate the process, give a small guided exercise, then let the learner make their own version and receive feedback.
10. For real-world tasks involving a specific model, version, tool, recipe, software release, or other variant, use the exact variant when known. If a critical detail changes the procedure and is unknown, ask for it instead of guessing.
11. Never invent measurements, torque values, compatibility claims, safety requirements, or other model-specific facts. Prefer manufacturer documentation or clearly label information that must be verified.
12. Include useful memory tricks, shortcuts, patterns, checklists, and best practices when they genuinely improve learning. Do not add gimmicks just to make the lesson longer.
13. Give the learner a chance to try the skill after seeing the demonstration. Then check their result and explain mistakes constructively.
14. If the learner says they do not understand, change the teaching approach: simpler wording, analogy, visual, worked example, or guided practice instead of repeating the same explanation.
15. End lessons with a concise recap and a realistic next step or application.
16. Adapt difficulty from the learner's demonstrated knowledge and performance rather than forcing everyone through the same path.
17. For potentially hazardous physical activities, prioritize safety, use appropriate warnings, avoid encouraging unsafe behavior, and recommend qualified adult/professional help when the task exceeds a learner's safe ability or requires specialized equipment.
18. Never pretend that a visual, demonstration, external source, tool, or real-world action was performed if it was not actually available. Be transparent about what is being shown or inferred.

OUTPUT STYLE:
- Be clear, concrete, encouraging, and concise without being shallow.
- Prefer headings, numbered steps, checklists, diagrams/visual specifications, worked examples, and short practice tasks over giant paragraphs.
- When appropriate, explicitly label: WHY IT MATTERS, WHAT YOU NEED, WATCH/SHOW, DO THIS, CHECK YOUR WORK, COMMON MISTAKE, and TRY IT.
- The learner should leave each lesson knowing both what to do and why it works.
`;

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

  // Enforce the product-wide teaching behavior at the secure backend boundary so every
  // AI surface (course builder, tutor, lessons, practice, and future features) follows it.
  if (typeof body === "object" && body !== null) {
    const request = body as { messages?: unknown };
    if (Array.isArray(request.messages)) {
      const messages = [...request.messages] as Array<{ role?: string; content?: unknown }>;
      const systemIndex = messages.findIndex((message) => message?.role === "system");
      if (systemIndex >= 0) {
        const original = messages[systemIndex].content;
        messages[systemIndex] = {
          ...messages[systemIndex],
          content: `${LEARN_ANYTHING_TEACHING_CONTRACT}\n\nPRODUCT-SPECIFIC INSTRUCTIONS:\n${typeof original === "string" ? original : "Follow the user's request while applying the teaching contract."}`,
        };
      } else {
        messages.unshift({ role: "system", content: LEARN_ANYTHING_TEACHING_CONTRACT });
      }
      body = { ...(body as Record<string, unknown>), messages };
    }
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
