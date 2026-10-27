package com.rork.learnanything.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val PATHS = "paths"
    const val TUTOR = "tutor"
    const val STUDY = "study"
    const val PROFILE = "profile"
    const val ONBOARDING = "onboarding"
    const val FLASHCARDS = "flashcards"
    const val NOTES = "notes"
    const val MISTAKES = "mistakes"
    const val PRICING = "pricing"

    val TABS = listOf(HOME, PATHS, TUTOR, STUDY, PROFILE)

    private fun e(s: String) = Uri.encode(s)

    fun intake(goal: String = "") = "intake?goal=${e(goal)}"
    fun lesson(courseId: String, lessonId: String) = "lesson/${e(courseId)}/${e(lessonId)}"
    fun session(topic: String = "", minutes: Int = 15) = "session?topic=${e(topic)}&minutes=$minutes"
    /** mode: challenge | quiz | mock | mistakes */
    fun quiz(mode: String, topic: String = "", examId: String = "") = "quiz?mode=$mode&topic=${e(topic)}&examId=${e(examId)}"
    fun flashcards(generate: String = "") = "flashcards?generate=${e(generate)}"
    fun deck(id: String) = "deck/${e(id)}"
    fun review(deckId: String = "all") = "review/${e(deckId)}"
    fun note(id: String = "new") = "note/${e(id)}"
    fun exam(id: String = "") = "exam?id=${e(id)}"
    /** from: "onboarding" lets people skip; next: route to open after finishing onboarding. */
    fun auth(from: String = "", next: String = "") = "auth?from=$from&next=${e(next)}"
}
