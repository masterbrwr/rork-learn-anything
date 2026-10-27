package com.rork.learnanything.data

/** Subject worlds: every topic is classified into a world that drives its color, icon and illustrations. */
object Worlds {
    private val rules: List<Pair<String, List<String>>> = listOf(
        "math" to listOf("math", "algebra", "calculus", "geometry", "trig", "statistic", "fraction", "equation", "number", "probability", "arithmetic", "precalc"),
        "programming" to listOf("python", "code", "coding", "program", "javascript", "web", "website", "software", "kotlin", "swift", "java", "sql", "html", "css", "react", "algorithm", "ai ", "artificial", "machine learning", "automation", "computer"),
        "mechanics" to listOf("engine", "car", "motor", "mechanic", "vehicle", "torque", "bike", "motorcycle", "engineering", "transmission", "brake", "turbo"),
        "science" to listOf("physics", "biology", "chemistry", "science", "cell", "mitosis", "photosynthesis", "atom", "force", "energy", "molecule", "dna", "gene", "astronomy", "space", "evolution", "anatomy", "ecology"),
        "history" to listOf("history", "war", "empire", "ancient", "revolution", "civilization", "medieval", "century", "politic"),
        "business" to listOf("business", "invest", "stock", "finance", "money", "economic", "market", "startup", "entrepreneur", "marketing", "accounting", "budget", "crypto", "sales"),
        "language" to listOf("spanish", "french", "english", "german", "japanese", "chinese", "language", "grammar", "writing", "essay", "vocabulary", "italian", "korean", "literature", "poetry"),
        "music" to listOf("music", "guitar", "piano", "song", "chord", "rhythm", "drum", "sing", "theory"),
        "sports" to listOf("football", "soccer", "basketball", "tennis", "fitness", "sport", "running", "gym", "workout", "golf", "swim"),
        "mind" to listOf("psychology", "mind", "habit", "philosophy", "productivity", "meditation", "emotion", "life skill", "career", "interview", "leadership", "communication"),
        "geography" to listOf("geography", "country", "map", "climate", "continent", "ocean", "city", "earth", "volcano"),
    )

    fun classify(text: String): String {
        val t = " ${text.lowercase()} "
        return rules.firstOrNull { (_, keys) -> keys.any { t.contains(it) } }?.first ?: "general"
    }

    val displayName = mapOf(
        "math" to "Math", "programming" to "Programming", "mechanics" to "Mechanics", "science" to "Science",
        "history" to "History", "business" to "Business", "language" to "Languages", "music" to "Music",
        "sports" to "Sports", "mind" to "Mind & Career", "geography" to "Geography", "general" to "General",
    )
}
