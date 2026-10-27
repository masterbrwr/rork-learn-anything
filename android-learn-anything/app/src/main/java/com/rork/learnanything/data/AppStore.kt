package com.rork.learnanything.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.util.TimeZone
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

fun today(): Long {
    val now = System.currentTimeMillis()
    return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
}

fun newId(): String = UUID.randomUUID().toString().take(12)

data class Achievement(val id: String, val title: String, val detail: String, val icon: String)

val ACHIEVEMENTS = listOf(
    Achievement("first_path", "Pathfinder", "Build your first learning path", "route"),
    Achievement("first_lesson", "First Lesson", "Complete your first lesson", "lesson"),
    Achievement("first_quiz", "First Quiz", "Finish your first quiz", "quiz"),
    Achievement("streak_7", "7-Day Streak", "Learn 7 days in a row", "flame"),
    Achievement("streak_30", "30-Day Streak", "Learn 30 days in a row", "flame"),
    Achievement("lessons_100", "Centurion", "Complete 100 lessons", "lesson"),
    Achievement("hours_10", "10 Hours Learned", "Spend 10 hours learning", "clock"),
    Achievement("mastered", "First Subject Mastered", "Finish an entire path", "trophy"),
    Achievement("explorer", "Explorer", "Explore 5 different subjects", "globe"),
    Achievement("quiz_master", "Quiz Master", "Score 90%+ on 5 quizzes", "star"),
    Achievement("problem_solver", "Problem Solver", "Answer 50 questions correctly", "bolt"),
    Achievement("card_shark", "Card Shark", "Review 100 flashcards", "cards"),
    Achievement("note_taker", "Note Taker", "Write 5 notes", "note"),
    Achievement("focused", "Deep Focus", "Complete 3 study sessions", "timer"),
)

/** Level curve: level n needs 120 * n * (n + 1) / 2 total XP. */
fun levelFor(xp: Int): Int {
    var lvl = 1
    while (120 * lvl * (lvl + 1) / 2 <= xp) lvl++
    return lvl
}

fun levelProgress(xp: Int): Float {
    val lvl = levelFor(xp)
    val start = 120 * (lvl - 1) * lvl / 2
    val end = 120 * lvl * (lvl + 1) / 2
    return (xp - start).toFloat() / (end - start)
}

@OptIn(FlowPreview::class)
object AppStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _unlockQueue = MutableStateFlow<List<Achievement>>(emptyList())
    val unlockQueue: StateFlow<List<Achievement>> = _unlockQueue.asStateFlow()

    /** Shared hand-off for prompts sent to the tutor tab from elsewhere in the app. */
    val pendingTutorPrompt = MutableStateFlow<String?>(null)

    private var file: File? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val f = File(context.filesDir, "learn_anything_state.json")
        file = f
        val loaded = runCatching {
            if (f.exists()) json.decodeFromString(AppState.serializer(), f.readText()) else null
        }.onFailure { Log.e("AppStore", "Failed to load state", it) }.getOrNull()
        _state.value = loaded?.let { Seed.upgrade(it) } ?: Seed.initialState()
        refreshStreak()
        _state.drop(1).debounce(400).onEach { persist(it) }.launchIn(scope)
    }

    private fun persist(s: AppState) {
        runCatching {
            val f = file ?: return
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(AppState.serializer(), s))
            tmp.renameTo(f)
        }.onFailure { Log.e("AppStore", "Failed to persist", it) }
    }

    fun update(block: (AppState) -> AppState) {
        _state.update(block)
        checkAchievements()
    }

    /** Cloud sync: snapshot without local-only logs. */
    fun exportJson(s: AppState = state.value): kotlinx.serialization.json.JsonElement =
        json.encodeToJsonElement(AppState.serializer(), s.copy(events = emptyList(), errorLog = emptyList()))

    fun importJson(el: kotlinx.serialization.json.JsonElement): AppState? =
        runCatching { json.decodeFromJsonElement(AppState.serializer(), el) }.getOrNull()

    /** Replace learning data with a cloud copy, keeping this device's theme and logs. */
    fun replaceState(remote: AppState) {
        _state.update { cur -> remote.copy(themeMode = cur.themeMode, events = cur.events, errorLog = cur.errorLog, isPremium = cur.isPremium) }
        refreshStreak()
    }

    fun consumeUnlock() {
        _unlockQueue.update { it.drop(1) }
    }

    // ---------- Analytics & errors ----------
    fun track(name: String, props: Map<String, String> = emptyMap()) {
        _state.update { s ->
            s.copy(events = (s.events + AnalyticsEvent(name, System.currentTimeMillis(), props)).takeLast(500))
        }
    }

    fun logError(where: String, t: Throwable) {
        Log.e("LearnAnything", where, t)
        _state.update { s -> s.copy(errorLog = (s.errorLog + "${System.currentTimeMillis()} $where: ${t.message}").takeLast(50)) }
    }

    // ---------- Activity / streak / XP ----------
    private fun refreshStreak() {
        _state.update { s ->
            val d = today()
            if (s.lastActiveDay != 0L && d - s.lastActiveDay > 1) s.copy(streak = 0) else s
        }
    }

    fun recordActivity(xp: Int, minutes: Int = 0) {
        update { s ->
            val d = today()
            val streak = when {
                s.lastActiveDay == d -> max(s.streak, 1)
                s.lastActiveDay == d - 1 -> s.streak + 1
                else -> 1
            }
            val existing = s.activity.firstOrNull { it.day == d } ?: DayActivity(d)
            val updated = existing.copy(minutes = existing.minutes + minutes, xp = existing.xp + xp)
            s.copy(
                xp = s.xp + xp,
                streak = streak,
                bestStreak = max(s.bestStreak, streak),
                lastActiveDay = d,
                activity = (s.activity.filter { it.day != d } + updated).sortedBy { it.day }.takeLast(120),
            )
        }
    }

    fun recordAnswer(topic: String, correct: Boolean) {
        if (topic.isBlank()) return
        update { s ->
            val key = topic.trim()
            val cur = s.topicStats.firstOrNull { it.topic.equals(key, true) } ?: TopicStat(key)
            val next = if (correct) cur.copy(correct = cur.correct + 1, lastSeen = System.currentTimeMillis())
            else cur.copy(wrong = cur.wrong + 1, lastSeen = System.currentTimeMillis())
            s.copy(
                topicStats = (s.topicStats.filterNot { it.topic.equals(key, true) } + next).takeLast(200),
                correctAnswers = s.correctAnswers + if (correct) 1 else 0,
            )
        }
    }

    fun addMistake(subject: String, step: LessonStep, yourAnswer: String) {
        val correct = when (step.type) {
            "fill" -> step.answerText
            else -> step.options.getOrNull(step.answer) ?: step.answerText
        }
        update { s ->
            s.copy(
                mistakes = (s.mistakes + Mistake(
                    id = newId(), subject = subject,
                    concept = step.concept.ifBlank { subject },
                    question = step.body.ifBlank { step.title },
                    yourAnswer = yourAnswer, correctAnswer = correct,
                    explanation = step.explanation,
                )).takeLast(300)
            )
        }
    }

    // ---------- Courses ----------
    fun saveCourse(course: Course) {
        update { s ->
            val exists = s.courses.any { it.id == course.id }
            s.copy(
                courses = if (exists) s.courses.map { if (it.id == course.id) course else it } else listOf(course) + s.courses,
                activeCourseId = course.id,
            )
        }
    }

    fun setActiveCourse(id: String) {
        update { s ->
            s.copy(activeCourseId = id, courses = s.courses.map { if (it.id == id) it.copy(lastOpened = System.currentTimeMillis()) else it })
        }
    }

    fun deleteCourse(id: String) {
        update { s ->
            val rest = s.courses.filter { it.id != id }
            s.copy(courses = rest, activeCourseId = if (s.activeCourseId == id) rest.firstOrNull()?.id else s.activeCourseId)
        }
    }

    fun mapLesson(courseId: String, lessonId: String, block: (LessonRef) -> LessonRef) {
        update { s ->
            s.copy(courses = s.courses.map { c ->
                if (c.id != courseId) c else c.copy(modules = c.modules.map { m ->
                    m.copy(lessons = m.lessons.map { if (it.id == lessonId) block(it) else it })
                })
            })
        }
    }

    /**
     * Adaptive engine: called when a lesson finishes. Adjusts difficulty, inserts a strengthening lesson when
     * the learner struggled, and rewrites the course's adaptive note.
     */
    fun completeLesson(courseId: String, lessonId: String, accuracy: Float, minutes: Int) {
        val course = state.value.courses.firstOrNull { it.id == courseId } ?: return
        val lesson = course.allLessons.firstOrNull { it.id == lessonId } ?: return
        val struggled = accuracy in 0f..0.59f
        val strong = accuracy >= 0.85f
        val newDifficulty = when {
            strong -> (course.difficulty + 1).coerceAtMost(5)
            struggled -> (course.difficulty - 1).coerceAtLeast(1)
            else -> course.difficulty
        }
        val note = when {
            struggled -> "You found \"${lesson.title}\" tricky, so I added a strengthening lesson with simpler examples."
            strong -> "You're flying through this. I've raised the difficulty for the next lessons."
            else -> course.adaptiveNote.ifBlank { "Path tuned to your answers so far." }
        }
        update { s ->
            s.copy(
                lessonsCompleted = s.lessonsCompleted + 1,
                courses = s.courses.map { c ->
                    if (c.id != courseId) c else c.copy(
                        difficulty = newDifficulty,
                        adaptiveNote = note,
                        lastOpened = System.currentTimeMillis(),
                        modules = c.modules.map { m ->
                            val idx = m.lessons.indexOfFirst { it.id == lessonId }
                            if (idx < 0) m else {
                                val list = m.lessons.toMutableList()
                                list[idx] = list[idx].copy(status = "done", accuracy = accuracy)
                                val alreadyRemedial = list.any { it.remedial && it.title.endsWith(lesson.title) }
                                if (struggled && !lesson.remedial && !alreadyRemedial) {
                                    list.add(idx + 1, LessonRef(
                                        id = newId(), title = "Strengthen: ${lesson.title}",
                                        minutes = 8, status = "review", remedial = true,
                                        note = "extra practice",
                                    ))
                                }
                                m.copy(lessons = list)
                            }
                        }
                    )
                }
            )
        }
        recordActivity(xp = 50 + (accuracy.coerceAtLeast(0f) * 30).roundToInt(), minutes = minutes)
        track("lesson_completed", mapOf("course" to course.title, "lesson" to lesson.title, "accuracy" to accuracy.toString()))
        if (state.value.courses.firstOrNull { it.id == courseId }?.isComplete == true) track("course_completed", mapOf("course" to course.title))
    }

    // ---------- Flashcards (SM-2 style spaced repetition) ----------
    fun addDeck(name: String, subject: String, cards: List<Pair<String, String>>): String {
        val deckId = newId()
        update { s ->
            s.copy(
                decks = listOf(Deck(deckId, name, subject, Worlds.classify(subject + " " + name))) + s.decks,
                cards = s.cards + cards.map { Flashcard(newId(), deckId, it.first, it.second, dueDay = today()) },
            )
        }
        return deckId
    }

    fun addCards(deckId: String, cards: List<Pair<String, String>>) {
        update { s -> s.copy(cards = s.cards + cards.map { Flashcard(newId(), deckId, it.first, it.second, dueDay = today()) }) }
    }

    fun deleteCard(id: String) = update { s -> s.copy(cards = s.cards.filter { it.id != id }) }

    fun deleteDeck(id: String) = update { s -> s.copy(decks = s.decks.filter { it.id != id }, cards = s.cards.filter { it.deckId != id }) }

    /** rating: 0 Again, 1 Hard, 2 Good, 3 Easy */
    fun rateCard(cardId: String, rating: Int) {
        update { s ->
            s.copy(cardReviews = s.cardReviews + 1, cards = s.cards.map { c ->
                if (c.id != cardId) c else {
                    val q = intArrayOf(1, 3, 4, 5)[rating.coerceIn(0, 3)]
                    val ease = (c.ease + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))).coerceAtLeast(1.3f)
                    if (rating == 0) {
                        c.copy(ease = ease, reps = 0, lapses = c.lapses + 1, intervalDays = 0, dueDay = today())
                    } else {
                        val interval = when (c.reps) {
                            0 -> if (rating == 1) 1 else if (rating == 3) 3 else 1
                            1 -> if (rating == 3) 7 else if (rating == 1) 3 else 6
                            else -> (c.intervalDays * ease * (if (rating == 1) 0.8f else if (rating == 3) 1.3f else 1f)).roundToInt().coerceAtLeast(1)
                        }
                        c.copy(ease = ease, reps = c.reps + 1, intervalDays = interval, dueDay = today() + interval)
                    }
                }
            })
        }
        recordActivity(xp = 2)
    }

    // ---------- Notes ----------
    fun saveNote(note: Note) {
        update { s ->
            val exists = s.notes.any { it.id == note.id }
            s.copy(notes = if (exists) s.notes.map { if (it.id == note.id) note else it } else listOf(note) + s.notes)
        }
    }

    fun deleteNote(id: String) = update { s -> s.copy(notes = s.notes.filter { it.id != id }) }

    // ---------- Tutor quota ----------
    const val FREE_TUTOR_DAILY = 25
    const val FREE_PATHS = 3

    fun tutorRemaining(s: AppState = state.value): Int {
        if (s.isPremium) return Int.MAX_VALUE
        val used = if (s.tutorDay == today()) s.tutorCountToday else 0
        return (FREE_TUTOR_DAILY - used).coerceAtLeast(0)
    }

    fun consumeTutor() {
        update { s ->
            val d = today()
            s.copy(tutorDay = d, tutorCountToday = if (s.tutorDay == d) s.tutorCountToday + 1 else 1)
        }
    }

    // ---------- Achievements ----------
    private fun checkAchievements() {
        val s = _state.value
        val minutes = s.activity.sumOf { it.minutes }
        val worlds = (s.courses.map { it.world } + s.decks.map { it.world }).toSet().size
        val earned = buildList {
            if (s.courses.any { !it.id.startsWith("seed") }) add("first_path")
            if (s.lessonsCompleted >= 1) add("first_lesson")
            if (s.quizzesTaken >= 1) add("first_quiz")
            if (s.streak >= 7) add("streak_7")
            if (s.streak >= 30) add("streak_30")
            if (s.lessonsCompleted >= 100) add("lessons_100")
            if (minutes >= 600) add("hours_10")
            if (s.courses.any { it.isComplete }) add("mastered")
            if (worlds >= 5) add("explorer")
            if (s.strongQuizzes >= 5) add("quiz_master")
            if (s.correctAnswers >= 50) add("problem_solver")
            if (s.cardReviews >= 100) add("card_shark")
            if (s.notes.size >= 5) add("note_taker")
            if (s.sessionsCompleted >= 3) add("focused")
        }
        val fresh = earned.filter { it !in s.unlocked }
        if (fresh.isNotEmpty()) {
            _state.update { it.copy(unlocked = it.unlocked + fresh) }
            _unlockQueue.update { q -> q + fresh.mapNotNull { id -> ACHIEVEMENTS.firstOrNull { it.id == id } } }
            fresh.forEach { track("achievement_unlocked", mapOf("id" to it)) }
        }
    }

    fun finishQuiz(correct: Int, total: Int, subject: String, mode: String) {
        val pct = if (total == 0) 0f else correct.toFloat() / total
        update { s ->
            s.copy(
                quizzesTaken = s.quizzesTaken + 1,
                strongQuizzes = s.strongQuizzes + if (pct >= 0.9f) 1 else 0,
            )
        }
        track("quiz_completed", mapOf("subject" to subject, "mode" to mode, "score" to "$correct/$total"))
    }

    /** Topics where the learner is weakest, used to personalize the home screen and tutor. */
    fun weakTopics(s: AppState = state.value, limit: Int = 3): List<TopicStat> =
        s.topicStats.filter { it.wrong > 0 && it.wrong >= it.correct * 0.5 }
            .sortedWith(compareByDescending<TopicStat> { it.wrong - it.correct }.thenByDescending { it.lastSeen })
            .take(limit)

    fun learnerContext(s: AppState = state.value): String {
        val weak = weakTopics(s).joinToString { it.topic }
        val strong = s.topicStats.filter { it.correct >= 3 && it.wrong <= it.correct / 4 }.take(4).joinToString { it.topic }
        val course = s.courses.firstOrNull { it.id == s.activeCourseId }
        val recentMistakes = s.mistakes.filter { !it.resolved }.takeLast(4).joinToString("; ") { "${it.concept}: answered '${it.yourAnswer}' instead of '${it.correctAnswer}'" }
        return buildString {
            append("Learner: ${s.profile.name.ifBlank { "the learner" }}. ")
            append("Preferred style: ${s.profile.learningStyle}. Daily time: ${s.profile.dailyGoalMinutes} min. ")
            if (s.profile.interests.isNotEmpty()) append("Interests: ${s.profile.interests.joinToString()}. ")
            if (course != null) append("Current path: ${course.title} (${(course.progress * 100).roundToInt()}% done, current lesson: ${course.currentLesson?.title ?: "complete"}, difficulty ${course.difficulty}/5). ")
            if (weak.isNotBlank()) append("Weak areas: $weak. ")
            if (strong.isNotBlank()) append("Strengths: $strong. ")
            if (recentMistakes.isNotBlank()) append("Recent mistakes: $recentMistakes.")
        }
    }
}
