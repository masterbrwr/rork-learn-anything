package com.rork.learnanything.data

/** Compatibility helpers for the adaptive learning screens. */
fun AppStore.learnerContext(): String {
    val s = state.value
    val profile = s.profile
    val interests = profile.interests.joinToString().ifBlank { "none listed" }
    val weak = weakTopics(s, 5).joinToString { it.topic }
    return buildString {
        append("Learner name: ${profile.name.ifBlank { "Learner" }}. ")
        append("Preferred language: ${profile.language}. ")
        append("Learning style: ${profile.learningStyle}. ")
        append("Daily goal: ${profile.dailyGoalMinutes} minutes. ")
        append("Interests: $interests. ")
        if (weak.isNotBlank()) append("Topics needing more practice: $weak. ")
        append("Current streak: ${s.streak} days. ")
        append("Completed lessons: ${s.lessonsCompleted}.")
    }
}

fun AppStore.weakTopics(s: AppState = state.value, limit: Int = 5): List<TopicStat> =
    s.topicStats
        .filter { it.wrong > 0 }
        .sortedWith(compareByDescending<TopicStat> { it.wrong - it.correct }.thenByDescending { it.lastSeen })
        .take(limit.coerceIn(1, 20))

fun AppStore.finishQuiz(correct: Int, graded: Int, subject: String, mode: String = "quiz") {
    val safeGraded = graded.coerceAtLeast(0)
    val score = if (safeGraded == 0) 0 else (correct.coerceIn(0, safeGraded) * 100 / safeGraded)
    update { s ->
        s.copy(
            quizzesTaken = s.quizzesTaken + 1,
            quizBestScore = maxOf(s.quizBestScore, score),
            strongQuizzes = s.strongQuizzes + if (score >= 90) 1 else 0,
            studySessions = if (mode == "mock") s.studySessions + 1 else s.studySessions,
        )
    }
    track("quiz_completed", mapOf("subject" to subject, "mode" to mode, "score" to score.toString()))
}
