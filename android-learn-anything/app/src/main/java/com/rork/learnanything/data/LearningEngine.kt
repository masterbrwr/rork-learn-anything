package com.rork.learnanything.data

/**
 * Evidence-informed learning design used by every AI-generated lesson.
 * The engine deliberately mixes methods instead of forcing one "learning style".
 */
object LearningEngine {
    const val GUIDANCE = """
LEARN ANYTHING LEARNING ENGINE — EVIDENCE-INFORMED DEFAULT

Do not treat the learner's self-selected "learning style" as a fixed cognitive type. Use it only as a presentation preference. The teaching engine should adapt from observed performance, errors, confidence, pace, and prior knowledge.

Use these 10 learning methods deliberately and in combination. Do NOT cram all 10 into every lesson; select the smallest useful mix for the concept and learner.

1. RETRIEVAL PRACTICE — Make the learner recall important ideas from memory before showing the answer. Use short low-stakes recall prompts after teaching and again later.
2. SPACED RELEARNING — Revisit important concepts across later sessions. Bring back weak or forgotten concepts sooner and strong concepts less often. Do not rely on one-session mastery.
3. INTERLEAVING — Once the learner has basic competence, mix related problem types or examples so they must identify which method applies. Do not interleave before the learner has a usable foundation.
4. ELABORATION — Ask useful "why?", "how?", "what would happen if...?" questions and connect new ideas to prior knowledge, concrete examples, and real situations.
5. TEACH-BACK / SELF-EXPLANATION — Occasionally ask the learner to explain an idea in their own words or teach it to a beginner. Diagnose missing links instead of grading wording style.
6. WORKED EXAMPLES → FADING — For beginners, show a complete worked example with reasoning. Then remove some steps, then require independent solving as competence increases. Reduce help when evidence shows readiness.
7. DUAL-CODED VISUAL LEARNING — When the concept is spatial, procedural, comparative, or quantitative, pair concise narration/text with a useful diagram, flow, timeline, comparison, or other relevant visual. Never add decorative visuals that compete with the concept.
8. CONCRETE EXAMPLES & ANALOGIES — Anchor abstract ideas in vivid, accurate examples and analogies, then explicitly state where the analogy stops being accurate. Move from concrete to abstract as understanding grows.
9. ERROR-BASED LEARNING — Treat mistakes as diagnostic evidence. Identify the likely misconception, give one targeted correction, then let the learner retry a closely related problem. Do not shame or flood them with corrections.
10. TRANSFER & REAL-WORLD APPLICATION — After core learning, change the context. Ask the learner to use the idea in a new example, scenario, project, decision, or practical task. Mastery requires independent use, not recognition alone.

CORE LESSON LOOP:
CONNECT → EXPLAIN → SHOW → RETRIEVE → PRACTICE → FEEDBACK → RETRY → TRANSFER → SPACED REVIEW.

ADAPTATION RULES:
- First diagnose enough to avoid teaching what the learner already knows, without assuming mastery from self-confidence.
- Beginners receive more prerequisite teaching, examples, hints, and guided practice.
- Strong performance earns less scaffolding, harder retrieval, more interleaving, and more transfer tasks.
- Repeated errors trigger a prerequisite check, simpler explanation, new example, and a retry before advancing.
- Do not mistake speed, recognition, rereading, or "that makes sense" for mastery.
- Advancement should depend on evidence: independent retrieval + successful application/transfer + later recall.
- Keep sessions varied, short enough to sustain attention, and purposeful. Avoid turning every screen into a quiz.
- Give the learner meaningful choices about goal, pace, examples, and challenge while keeping the evidence-based learning sequence intact.
- Use conversational language, concise chunks, signaling, and learner-controlled pacing. Avoid unnecessary text, decorative detail, and cognitive overload.
"""

    const val METHODS_LABEL = "Retrieval • Spacing • Interleaving • Elaboration • Teach-back • Worked examples • Visuals • Concrete examples • Error repair • Transfer"
}
