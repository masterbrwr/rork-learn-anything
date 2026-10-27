package com.rork.learnanything.data

import com.rork.learnanything.Config
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AiException(message: String, val retryable: Boolean = true) : Exception(message)

@Serializable data class DraftLesson(val title: String = "", val minutes: Int = 10)
@Serializable data class DraftModule(val title: String = "", val lessons: List<DraftLesson> = emptyList())
@Serializable
data class CourseDraft(
    val title: String = "",
    val subject: String = "",
    val overview: String = "",
    val level: String = "",
    val objectives: List<String> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
    val foundationGap: String = "",
    val modules: List<DraftModule> = emptyList(),
)

@Serializable data class QuizPayload(val questions: List<LessonStep> = emptyList())
@Serializable data class CardDraft(val front: String = "", val back: String = "")
@Serializable data class CardsPayload(val deckName: String = "", val cards: List<CardDraft> = emptyList())
@Serializable
data class PatternReport(
    val insight: String = "",
    val rootCause: String = "",
    val prerequisite: String = "",
    val practiceTopic: String = "",
    val tips: List<String> = emptyList(),
)
@Serializable data class DraftTask(val text: String = "", val kind: String = "study")
@Serializable data class DraftDay(val label: String = "", val focus: String = "", val tasks: List<DraftTask> = emptyList())
@Serializable data class ExamPlanPayload(val keyConcepts: List<String> = emptyList(), val days: List<DraftDay> = emptyList())

data class TutorReply(val text: String, val visual: Visual?, val chips: List<String>)

/**
 * All AI goes through the Rork Toolkit proxy (Vercel AI Gateway). Primary model with gateway fallbacks.
 */
object Ai {
    private const val PRIMARY = "anthropic/claude-haiku-5.5"
    private val FALLBACKS = listOf("anthropic/claude-sonnet-5.5", "deepseek/deepseek-v4.1-flash")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    private val client by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 90_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 90_000
            }
        }
    }

    suspend fun complete(
        system: String,
        messages: List<Pair<String, String>>,
        maxTokens: Int = 1200,
        temperature: Double = 0.6,
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", PRIMARY)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                messages.forEach { (role, text) -> addJsonObject { put("role", role); put("content", text) } }
            }
            putJsonObject("providerOptions") {
                putJsonObject("gateway") { putJsonArray("models") { FALLBACKS.forEach { add(it) } } }
            }
        }
        val backend = Config.EXPO_PUBLIC_SUPABASE_URL.trimEnd('/')
        val anonKey = Config.EXPO_PUBLIC_SUPABASE_ANON_KEY
        if (backend.isBlank() || anonKey.isBlank()) {
            throw AiException("AI setup is not connected yet. The app needs its secure backend configuration.", retryable = false)
        }

        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val res = client.post("$backend/functions/v1/ai-chat") {
                    header("apikey", anonKey)
                    header("Authorization", "Bearer $anonKey")
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
                val text = res.bodyAsText()
                if (!res.status.isSuccess()) {
                    val code = res.status.value
                    if (code == 429 || code >= 500) throw AiException("Service busy ($code)") else throw AiException("Request failed ($code)", retryable = false)
                }
                val content = json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")
                val str = (content as? JsonPrimitive)?.content ?: content?.jsonArray?.joinToString("") {
                    it.jsonObject["text"]?.jsonPrimitive?.content ?: ""
                }
                if (str.isNullOrBlank()) throw AiException("Empty response")
                return@withContext str
            } catch (e: AiException) {
                lastError = e
                if (!e.retryable) throw e
            } catch (e: Exception) {
                lastError = e
            }
            if (attempt == 0) delay(800)
        }
        throw lastError ?: AiException("Unknown error")
    }

    private fun extractJson(raw: String): String {
        val s = raw.indexOf('{')
        val e = raw.lastIndexOf('}')
        if (s < 0 || e <= s) throw AiException("No JSON in response")
        return raw.substring(s, e + 1)
    }

    suspend fun <T> structured(system: String, user: String, serializer: KSerializer<T>, maxTokens: Int = 3000): T {
        var last: Exception? = null
        repeat(2) {
            try {
                val raw = complete("$system\nRespond with ONLY a single valid JSON object. No markdown, no commentary.", listOf("user" to user), maxTokens, 0.5)
                return json.decodeFromString(serializer, extractJson(raw))
            } catch (e: AiException) {
                if (!e.retryable) throw e
                last = e
            } catch (e: Exception) {
                last = e
            }
        }
        throw AiException("Couldn't read the AI response: ${last?.message}")
    }

    private const val STEP_SCHEMA = """Each step object: {"type": one of "concept"|"example"|"reveal"|"mcq"|"truefalse"|"fill"|"match"|"order"|"estimate"|"sort"|"tapwords", "title": short (for tapwords: the instruction, e.g. \"Tap the two noble gases\"), "body": text (concept/example: 2-4 vivid sentences; questions: the question), "options": [4 choices for mcq; ["True","False"] for truefalse], "answer": index of correct option, "answerText": single word/short phrase for fill (use ____ in body), "pairs": [{"left","right"}] x4 for match, "items": correct ordered list of 3-5 for order; for tapwords the 1-3 exact single words from body to tap, "estimate": the learner slides to guess a number: body is the question, answerText the exact number only (digits), "min" and "max" the slider range, "unit" (e.g. \"%\", \"km\", \"years\"), "tolerance" the +/- error still counted correct, "sort": "options" 2-3 short group names and "pairs" 5-6 of {"left": item, "right": its group name exactly as in options}, "reveal": ungraded self-test flip cards: "pairs" 3-4 of {"left": term or question, "right": answer under 14 words}, "tapwords": body is one or two natural sentences (under 30 words) containing the target words, "explanation": why the answer is right, "misconception": the likely misunderstanding behind a wrong answer, "simpler": a simpler analogy or example, "hint": a nudge without giving it away, "concept": the specific concept tested (2-4 words), "difficulty": 1-5, "visual": optional {"type": "flow"|"cycle"|"timeline"|"bars"|"graph"|"compare"|"formula", "title", "caption", "formula" (formula type only), "items":[{"label","value" (number for bars/graph),"detail"}]} }"""

    suspend fun generateCourse(goal: String, level: String, deadline: String, minutesPerDay: Int, style: String, learner: String): CourseDraft =
        structured(
            "You are the curriculum engine of Learn Anything, a world-class adaptive learning platform. Design focused, practical, well-sequenced learning paths.",
            """Learner goal: "$goal"
Current level: $level. Deadline: $deadline. Time per day: $minutesPerDay minutes. Preferred style: $style.
Context: $learner
Design a personalized path. If the goal implies a prerequisite gap for someone at this level (e.g. calculus without algebra), put a short foundation lesson first and explain it in foundationGap. Size the path to the deadline (tomorrow = 4-6 lessons; no rush = 10-14).
JSON schema: {"title": concise path title, "subject": broad subject (e.g. Biology), "overview": 1-2 sentences, "level": "Beginner"|"Intermediate"|"Advanced", "objectives": [3-5], "prerequisites": [1-3 concepts that come before], "nextSteps": [2-3 topics to learn after], "foundationGap": "" or one sentence, "modules": [{"title", "lessons": [{"title": specific, "minutes": 5-15}]}] (2-4 modules)}""",
            CourseDraft.serializer(), 2500,
        )

    suspend fun generateLesson(course: Course, lesson: LessonRef, learner: String): LessonContent =
        structured(
            "You are an expert teacher creating an interactive mobile lesson. Never write walls of text. Alternate between short teaching and interaction so the learner acts every minute. Use analogies, real-world examples and visuals.",
            """Path: ${course.title} (${course.subject}). Lesson: "${lesson.title}"${if (lesson.remedial) " (this is a strengthening lesson: go slower, use simpler examples and more practice)" else ""}.
Difficulty: ${course.difficulty}/5. Learner: $learner
Create 8-10 steps: start with a concept (with a visual), mix in an example, and at least 5 interactive steps using varied types; never use the same question type twice in a row. Include at least one estimate, sort or tapwords step (an estimate only when there is a real, surprising number), one reveal step to self-test vocabulary, and pick the rest from mcq, truefalse, fill, match and order. End with a scenario mcq applying the idea to the real world.
JSON: {"summary": one sentence, "keyPoints": [3-4 short takeaways], "steps": [steps]}
$STEP_SCHEMA""",
            LessonContent.serializer(), 4500,
        ).cleaned()

    suspend fun generateQuiz(topic: String, count: Int, difficulty: Int, learner: String, focus: String = ""): List<LessonStep> =
        structured(
            "You write excellent adaptive quiz questions with meaningful feedback for every answer.",
            """Topic: $topic. Number of questions: $count. Difficulty: $difficulty/5. ${if (focus.isNotBlank()) "Focus on: $focus." else ""}
Learner: $learner
Use mostly mcq with some truefalse and fill. Every question must have explanation, misconception, simpler and hint.
JSON: {"questions": [steps]}
$STEP_SCHEMA""",
            QuizPayload.serializer(), 3500,
        ).questions.filter { it.type in setOf("mcq", "truefalse", "fill") && (it.type == "fill" || it.options.size >= 2) }

    suspend fun similarQuestion(step: LessonStep, easier: Boolean): LessonStep =
        generateQuiz(
            topic = step.concept.ifBlank { step.body },
            count = 1,
            difficulty = (step.difficulty + if (easier) -1 else 0).coerceIn(1, 5),
            learner = AppStore.learnerContext(),
            focus = "A new question testing the same idea as: \"${step.body}\". ${if (easier) "Make it easier and more concrete." else ""}",
        ).firstOrNull() ?: throw AiException("No question generated")

    suspend fun generateSession(subject: String, topic: String, difficulty: Int, minutes: Int, learner: String): LessonContent =
        structured(
            "You build focused, timed study sessions that teach, practice, quiz and review.",
            """Build a $minutes-minute study session. Subject: $subject. Topic: $topic. Difficulty: $difficulty/5. Learner: $learner
Steps must carry "phase": "learn" (2-3 concept/example/reveal steps with visuals), "practice" (3-4 interactive steps with varied types: sort, estimate, tapwords, match, order, fill, mcq), "quiz" (4-5 mcq/truefalse/fill). Do not include review steps; review is generated from performance.
JSON: {"summary", "keyPoints": [3-5], "steps": [steps with phase]}
$STEP_SCHEMA""",
            LessonContent.serializer(), 4500,
        ).cleaned()

    /** Drops or downgrades AI steps that are malformed, so the player never shows a broken activity. */
    private fun LessonContent.cleaned(): LessonContent = copy(steps = steps.mapNotNull { it.cleaned() })

    private fun LessonStep.cleaned(): LessonStep? {
        fun norm(s: String) = s.lowercase().trim { !it.isLetterOrDigit() }
        return when (type) {
            "estimate" -> {
                val n = answerText.replace(",", "").let { Regex("-?\\d+(\\.\\d+)?").find(it)?.value }?.toFloatOrNull() ?: return null
                copy(answerText = n.toString(), tolerance = tolerance.coerceAtLeast(0f))
            }
            "sort" -> {
                val groups = options.filter { it.isNotBlank() }.ifEmpty { pairs.map { it.right }.distinct() }
                val ok = groups.size in 2..3 && pairs.size >= 3 && pairs.all { p -> groups.any { it.trim().equals(p.right.trim(), true) } }
                if (ok) copy(options = groups) else null
            }
            "tapwords" -> {
                val words = body.split(Regex("\\s+")).map(::norm).toSet()
                val targets = items.map(::norm).filter { it.isNotEmpty() }
                if (targets.isNotEmpty() && targets.all { it in words }) this else null
            }
            "reveal" -> if (pairs.size >= 2) this else copy(type = "concept")
            "mcq" -> if (options.size >= 2) copy(answer = answer.coerceIn(0, options.lastIndex)) else null
            "match" -> if (pairs.size >= 2) this else null
            "order" -> if (items.size >= 2) this else null
            "fill" -> if (answerText.isNotBlank()) this else null
            else -> this
        }
    }

    suspend fun flashcards(source: String, count: Int = 10): CardsPayload =
        structured(
            "You create high-quality spaced-repetition flashcards: one idea per card, concise fronts, precise backs.",
            """Create $count flashcards from this material (a topic name or notes):
\"\"\"${source.take(8000)}\"\"\"
JSON: {"deckName": short name, "cards": [{"front": question or term, "back": answer under 25 words}]}""",
            CardsPayload.serializer(), 2500,
        )

    suspend fun mistakePatterns(mistakes: List<Mistake>): PatternReport =
        structured(
            "You are a diagnostic tutor. Find the underlying pattern and missing prerequisite behind a learner's mistakes. Be specific and kind.",
            """Mistakes:
${mistakes.takeLast(25).joinToString("\n") { "- [${it.subject} / ${it.concept}] Q: ${it.question} | answered: ${it.yourAnswer} | correct: ${it.correctAnswer}" }}
JSON: {"insight": "You frequently ... (one sentence, second person)", "rootCause": one sentence on the underlying misunderstanding, "prerequisite": the foundational concept to fix (2-5 words), "practiceTopic": topic for targeted practice, "tips": [3 short actionable tips]}""",
            PatternReport.serializer(), 1200,
        )

    suspend fun examPlan(subject: String, topics: String, daysLeft: Int, learner: String): ExamPlanPayload =
        structured(
            "You are an expert exam coach who builds realistic, spaced study schedules.",
            """Exam: $subject. Topics: ${topics.ifBlank { "the core of the subject" }}. Days until exam: $daysLeft. Learner: $learner
Create one entry per day from today up to the exam day (max 10; if more days, group them). The final day is a light review. Mix study, practice, flashcards and mock tests; prioritize weak areas early.
JSON: {"keyConcepts": [5-8 most important concepts], "days": [{"label": "Today" | "Tomorrow" | "Day 3"…, "focus": short, "tasks": [{"text": specific task, "kind": "study"|"practice"|"flashcards"|"mock"}] (2-4 tasks)}]}""",
            ExamPlanPayload.serializer(), 2500,
        )

    suspend fun notesAction(kind: String, notes: String): String {
        val instruction = when (kind) {
            "summarize" -> "Summarize these notes into a tight study summary: a one-line gist, then 4-7 bullet points of key ideas."
            "gaps" -> "Read these notes as a tutor. Identify what the learner seems not to understand yet, gaps, vague spots or likely misconceptions. Give 3-5 bullets, each with a quick fix."
            else -> "Explain the key ideas in these notes simply."
        }
        return complete(
            "You are a precise study assistant. Use short bullet points starting with '- '. Use **bold** for key terms. No headings.",
            listOf("user" to "$instruction\n\nNotes:\n\"\"\"${notes.take(8000)}\"\"\""), 900,
        )
    }

    val TUTOR_MODES = listOf("Explain", "Practice", "Quiz me", "Hint", "Explain differently", "Test me", "Review mistakes", "From scratch")

    suspend fun tutor(history: List<ChatMessage>, mode: String, learner: String, extraContext: String = ""): TutorReply {
        val modeGuide = when (mode) {
            "Practice" -> "Give one practice problem at a time, wait for the answer, then give feedback."
            "Quiz me" -> "Ask one quiz question at a time (multiple choice with A-D), wait for the answer, then grade with feedback."
            "Hint" -> "Give only a hint that moves them one step forward. Never the full answer."
            "Explain differently" -> "Explain the last idea in a completely different way: a new analogy or angle."
            "Test me" -> "Act as an examiner: harder questions, one at a time, grade strictly but kindly."
            "Review mistakes" -> "Walk through the learner's recent mistakes one at a time and help them fix the misunderstanding."
            "From scratch" -> "Assume zero knowledge. Start from first principles with everyday language."
            else -> "Explain clearly with an analogy and a concrete example, then check understanding with a question."
        }
        val system = """You are the Learn Anything tutor: a world-class, encouraging personal teacher. Teach with clear structure, not generic chat. Start by directly addressing the learner's question. Use plain language, then a concrete example or analogy, and break complicated ideas into numbered steps. Adapt the difficulty to the learner context. If they are confused, explain a different way without blaming them. For math/science, show the reasoning and units; for practical skills, give safe, realistic steps; for broad questions, give a useful answer before asking a follow-up. Keep most replies around 100-220 words (shorter for simple questions), use **bold** for key terms and bullets when useful. Ask at most one purposeful question at the end; don't force a question if the user asked for a direct fact. Never invent facts or claim you remember something not in the supplied context.
Mode: $mode. $modeGuide
What you know about the learner: $learner
$extraContext
When a diagram truly helps, add on its own final line: VISUAL: {"type":"flow"|"cycle"|"timeline"|"bars"|"graph"|"compare"|"formula","title":"...","caption":"...","formula":"...","items":[{"label":"...","value":0,"detail":"..."}]}
Always end with a line: CHIPS: reply option 1 | reply option 2 | reply option 3 (short things the learner might say next)."""
        val msgs = history.takeLast(16).map { (if (it.role == "user") "user" else "assistant") to it.text }
        val raw = complete(system, msgs, 900, 0.7)
        return parseTutor(raw)
    }

    fun parseTutor(raw: String): TutorReply {
        var visual: Visual? = null
        var chips = emptyList<String>()
        val kept = mutableListOf<String>()
        raw.lines().forEach { line ->
            val t = line.trim()
            when {
                t.startsWith("VISUAL:") -> visual = runCatching { json.decodeFromString(Visual.serializer(), extractJson(t.removePrefix("VISUAL:"))) }.getOrNull()
                t.startsWith("CHIPS:") -> chips = t.removePrefix("CHIPS:").split("|").map { it.trim() }.filter { it.isNotBlank() }.take(3)
                else -> kept += line
            }
        }
        return TutorReply(kept.joinToString("\n").trim(), visual, chips)
    }
}
