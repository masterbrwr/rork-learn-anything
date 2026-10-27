package com.rork.learnanything.data

import kotlinx.serialization.Serializable

@Serializable
data class LearnerProfile(
    val name: String = "",
    val interests: List<String> = emptyList(),
    val dailyGoalMinutes: Int = 20,
    val learningStyle: String = "Examples",
    val onboarded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class VisualItem(
    val label: String = "",
    val value: Float = 0f,
    val detail: String = "",
)

/** Structured visual the AI (or built-in content) can request. Rendered natively with Canvas. */
@Serializable
data class Visual(
    val type: String = "flow", // flow | cycle | timeline | bars | graph | compare | formula
    val title: String = "",
    val caption: String = "",
    val formula: String = "",
    val items: List<VisualItem> = emptyList(),
)

@Serializable
data class MatchPair(val left: String = "", val right: String = "")

/**
 * A single interactive step. Lessons, study sessions, quizzes and challenges are all built from these.
 * type: concept | example | reveal | mcq | truefalse | fill | match | order | estimate | sort | tapwords | engine_sim | engine_label
 */
@Serializable
data class LessonStep(
    val type: String = "concept",
    val title: String = "",
    val body: String = "",
    val options: List<String> = emptyList(),
    val answer: Int = 0,
    val answerText: String = "",
    val explanation: String = "",
    val misconception: String = "",
    val simpler: String = "",
    val hint: String = "",
    val pairs: List<MatchPair> = emptyList(),
    val items: List<String> = emptyList(),
    val visual: Visual? = null,
    val concept: String = "",
    val phase: String = "",
    val difficulty: Int = 2,
    /** estimate: slider range, unit and accepted +/- error. */
    val min: Float = 0f,
    val max: Float = 0f,
    val unit: String = "",
    val tolerance: Float = 0f,
)

@Serializable
data class LessonContent(
    val summary: String = "",
    val steps: List<LessonStep> = emptyList(),
    val keyPoints: List<String> = emptyList(),
)

@Serializable
data class LessonRef(
    val id: String,
    val title: String,
    val minutes: Int = 10,
    val status: String = "upcoming", // done | skipped | upcoming | review
    val note: String = "",
    val accuracy: Float = -1f,
    val remedial: Boolean = false,
    val content: LessonContent? = null,
)

@Serializable
data class CourseModule(
    val title: String,
    val lessons: List<LessonRef> = emptyList(),
)

@Serializable
data class Course(
    val id: String,
    val title: String,
    val subject: String = "General",
    val world: String = "general",
    val goal: String = "",
    val overview: String = "",
    val objectives: List<String> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
    val modules: List<CourseModule> = emptyList(),
    val adaptiveNote: String = "",
    val difficulty: Int = 2,
    val level: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpened: Long = System.currentTimeMillis(),
    val finalAssessmentScore: Int = -1,
) {
    val allLessons: List<LessonRef> get() = modules.flatMap { it.lessons }
    val doneCount: Int get() = allLessons.count { it.status == "done" }
    val skippedCount: Int get() = allLessons.count { it.status == "skipped" }
    val total: Int get() = allLessons.size
    val progress: Float
        get() = if (total == 0) 0f else (doneCount + skippedCount).toFloat() / total
    val currentLesson: LessonRef?
        get() = allLessons.firstOrNull { it.status == "upcoming" || it.status == "review" }
    val minutesLeft: Int
        get() = allLessons.filter { it.status == "upcoming" || it.status == "review" }.sumOf { it.minutes }
    val isComplete: Boolean get() = total > 0 && currentLesson == null
}

@Serializable
data class Flashcard(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val ease: Float = 2.5f,
    val intervalDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val dueDay: Long = 0,
)

@Serializable
data class Deck(
    val id: String,
    val name: String,
    val subject: String = "General",
    val world: String = "general",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val subject: String = "General",
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

@Serializable
data class Mistake(
    val id: String,
    val subject: String,
    val concept: String,
    val question: String,
    val yourAnswer: String,
    val correctAnswer: String,
    val explanation: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false,
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: String, // user | assistant
    val text: String,
    val visual: Visual? = null,
    val chips: List<String> = emptyList(),
    val time: Long = System.currentTimeMillis(),
)

@Serializable
data class ExamTask(val id: String, val text: String, val done: Boolean = false, val kind: String = "study")

@Serializable
data class ExamDay(val dayIndex: Int, val label: String, val focus: String, val tasks: List<ExamTask> = emptyList())

@Serializable
data class Exam(
    val id: String,
    val subject: String,
    val title: String,
    val examDay: Long,
    val topics: String = "",
    val keyConcepts: List<String> = emptyList(),
    val days: List<ExamDay> = emptyList(),
    val planStartDay: Long = 0,
    val mockScores: List<Int> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class DayActivity(val day: Long, val minutes: Int = 0, val xp: Int = 0)

@Serializable
data class AnalyticsEvent(val name: String, val time: Long, val props: Map<String, String> = emptyMap())

@Serializable
data class TopicStat(val topic: String, val correct: Int = 0, val wrong: Int = 0, val lastSeen: Long = 0)

@Serializable
data class AppState(
    val profile: LearnerProfile = LearnerProfile(),
    val courses: List<Course> = emptyList(),
    val activeCourseId: String? = null,
    val decks: List<Deck> = emptyList(),
    val cards: List<Flashcard> = emptyList(),
    val notes: List<Note> = emptyList(),
    val mistakes: List<Mistake> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val activity: List<DayActivity> = emptyList(),
    val topicStats: List<TopicStat> = emptyList(),
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDay: Long = 0,
    val unlocked: List<String> = emptyList(),
    val lessonsCompleted: Int = 0,
    val quizzesTaken: Int = 0,
    val strongQuizzes: Int = 0,
    val correctAnswers: Int = 0,
    val cardReviews: Int = 0,
    val sessionsCompleted: Int = 0,
    val challengeDay: Long = 0,
    val challengeResults: List<Boolean> = emptyList(),
    val tutorDay: Long = 0,
    val tutorCountToday: Int = 0,
    val themeMode: String = "system", // light | dark | system
    val isPremium: Boolean = false,
    val events: List<AnalyticsEvent> = emptyList(),
    val errorLog: List<String> = emptyList(),
    val tutorVoice: Boolean = false,
    /** "Listen instead of read": lessons, sessions and quizzes are narrated automatically. */
    val listenMode: Boolean = false,
    val listenAutoAdvance: Boolean = true,
    val narratorRate: Float = 1f,
    val narratorVoice: String = "",
)
