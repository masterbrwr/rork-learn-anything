package com.rork.learnanything.data

/** Built-in starter content so the app is useful offline on first launch. */
object Seed {
    const val ENGINE_COURSE_ID = "seed-engines"

    fun initialState(): AppState {
        val d = today()
        return AppState(
            courses = listOf(engineCourse()),
            activeCourseId = ENGINE_COURSE_ID,
            decks = listOf(Deck("seed-deck-engines", "Engine Basics", "Engines", "mechanics")),
            cards = engineCards.mapIndexed { i, (f, b) -> Flashcard("seed-card-$i", "seed-deck-engines", f, b, dueDay = d) },
        )
    }

    /** Latest built-in content for seeded lessons, used to upgrade older saved copies. */
    fun builtInContent(lessonId: String): LessonContent? = when (lessonId) {
        "seed-what" -> whatAnEngineDoes
        "seed-fourstroke" -> fourStroke
        else -> null
    }

    /** Refresh seeded lessons whose saved steps are out of date (keeps progress, swaps content). */
    fun upgrade(s: AppState): AppState {
        var changed = false
        val courses = s.courses.map { c ->
            if (c.id != ENGINE_COURSE_ID) c else c.copy(modules = c.modules.map { m ->
                m.copy(lessons = m.lessons.map { l ->
                    val fresh = builtInContent(l.id)
                    if (fresh != null && l.content?.steps != fresh.steps) { changed = true; l.copy(content = fresh) } else l
                })
            })
        }
        return if (changed) s.copy(courses = courses) else s
    }

    private val engineCards = listOf(
        "What are the four strokes, in order?" to "Intake, compression, power, exhaust.",
        "What does the crankshaft do?" to "Converts the piston's up-and-down motion into rotation.",
        "Why compress the air-fuel mixture?" to "A squeezed mixture burns faster and pushes harder, releasing more useful energy.",
        "What fires the mixture in a petrol engine?" to "The spark plug, near the top of the compression stroke.",
        "Which valve is open during exhaust?" to "The exhaust valve.",
        "How many crankshaft turns per four-stroke cycle?" to "Two full turns (720 degrees).",
    )

    private fun l(id: String, title: String, min: Int, content: LessonContent? = null) =
        LessonRef(id = "seed-$id", title = title, minutes = min, content = content)

    fun engineCourse() = Course(
        id = ENGINE_COURSE_ID,
        title = "Engine Fundamentals",
        subject = "Engines",
        world = "mechanics",
        goal = "Understand how car engines work",
        overview = "From the first spark to troubleshooting a rough idle: understand what happens inside an engine, part by part.",
        objectives = listOf(
            "Explain how fuel becomes motion",
            "Walk through the four-stroke cycle",
            "Name the key parts and what they do",
            "Diagnose common engine problems",
        ),
        prerequisites = listOf("Basic forces", "Pressure and heat"),
        nextSteps = listOf("Transmissions", "Turbocharging", "Electric motors"),
        adaptiveNote = "Tell me what you already know and I'll skip it. Struggle somewhere and I'll add practice.",
        modules = listOf(
            CourseModule("How engines breathe", listOf(
                l("what", "What an engine does", 8, whatAnEngineDoes),
                l("fourstroke", "Four-stroke cycle", 12, fourStroke),
                l("pistons", "Pistons", 10),
                l("crank", "Crankshaft", 10),
            )),
            CourseModule("Making power", listOf(
                l("valves", "Valvetrain", 14),
                l("fuel", "Fuel and air", 12),
                l("ignition", "Ignition", 10),
                l("compression", "Compression", 15),
            )),
            CourseModule("Keeping it alive", listOf(
                l("cooling", "Cooling", 10),
                l("lube", "Lubrication", 10),
                l("perf", "Performance", 12),
                l("trouble", "Troubleshooting", 15),
            )),
        ),
    )

    private val whatAnEngineDoes = LessonContent(
        summary = "An engine turns the chemical energy in fuel into motion.",
        keyPoints = listOf("Burning fuel creates hot, high-pressure gas", "Pressure pushes a piston", "The crankshaft turns that push into rotation"),
        steps = listOf(
            LessonStep(
                type = "concept", title = "Energy in, motion out",
                body = "A car engine is an energy converter. Fuel stores chemical energy. When it burns inside a sealed cylinder, the gas gets hot and expands violently. That pressure shoves a piston down, and a crankshaft turns the push into the spinning motion that drives the wheels.",
                visual = Visual("flow", "From fuel to wheels", items = listOf(
                    VisualItem("Fuel + air"), VisualItem("Combustion"), VisualItem("Pressure"), VisualItem("Piston push"), VisualItem("Rotation"),
                )),
                concept = "Energy conversion",
            ),
            LessonStep(
                type = "mcq", title = "Quick check",
                body = "What actually pushes the piston down?",
                options = listOf("The spark plug's electricity", "Expanding hot gas", "The crankshaft", "Engine oil"),
                answer = 1,
                explanation = "Burning fuel releases heat, the gas expands, and that pressure is what pushes the piston.",
                misconception = "The spark only starts the burn; it has almost no force of its own.",
                simpler = "Think of a balloon heating up in the sun: hotter gas pushes harder on its walls.",
                hint = "Think about what happens to gas when it gets very hot.",
                concept = "Combustion pressure",
            ),
            LessonStep(
                type = "estimate", title = "Make a guess",
                body = "Of all the energy in the fuel, roughly what percentage ends up actually moving a typical petrol car?",
                answerText = "30", min = 0f, max = 100f, unit = "%", tolerance = 10f,
                explanation = "Only about 25-35% of the fuel's energy becomes motion. The rest leaves as heat through the exhaust and the cooling system.",
                misconception = "Engines feel powerful, so it's natural to guess high, but most of the energy is lost as heat.",
                simpler = "Feel how hot the bonnet gets: that heat is energy that never reached the wheels.",
                hint = "Think about how hot an engine gets.",
                concept = "Efficiency",
            ),
            LessonStep(
                type = "reveal", title = "Flip the cards",
                body = "Four words you'll hear in every engine lesson. Guess each one, then tap to check.",
                pairs = listOf(
                    MatchPair("Combustion", "Fuel burning fast with oxygen, releasing heat"),
                    MatchPair("Stroke", "One full trip of the piston, up or down"),
                    MatchPair("Torque", "The twisting force on the crankshaft"),
                    MatchPair("Displacement", "Total volume the pistons sweep, in litres"),
                ),
                concept = "Engine vocabulary",
            ),
            LessonStep(
                type = "concept", title = "Why cylinders matter",
                body = "The cylinder is a sealed tube. Sealing traps the pressure so it can only escape by moving the piston. More cylinders means more power pulses per turn, which is why engines feel smoother with 6 or 8 cylinders.",
                visual = Visual("bars", "Power pulses per crank turn", items = listOf(
                    VisualItem("1 cyl", 0.5f), VisualItem("4 cyl", 2f), VisualItem("6 cyl", 3f), VisualItem("8 cyl", 4f),
                )),
                concept = "Cylinders",
            ),
            LessonStep(
                type = "truefalse", title = "True or false",
                body = "An engine creates energy.",
                options = listOf("True", "False"), answer = 1,
                explanation = "Energy can't be created. The engine converts chemical energy into motion and heat, and most of it is lost as heat.",
                misconception = "It's easy to think engines make power from nothing, but they only convert it.",
                simpler = "Like a water wheel: it doesn't make water, it uses flowing water.",
                concept = "Energy conversion",
            ),
            LessonStep(
                type = "sort", title = "Sort it out",
                body = "Does each one move, or stay still while the engine runs?",
                options = listOf("Moves", "Stays still"),
                pairs = listOf(
                    MatchPair("Piston", "Moves"), MatchPair("Crankshaft", "Moves"), MatchPair("Valves", "Moves"),
                    MatchPair("Cylinder wall", "Stays still"), MatchPair("Engine block", "Stays still"), MatchPair("Spark plug", "Stays still"),
                ),
                explanation = "The piston, crankshaft and valves move; the block, cylinder walls and spark plug stay fixed and guide them.",
                hint = "Valves open and close many times a second.",
                concept = "Engine parts",
            ),
            LessonStep(
                type = "tapwords", title = "Tap the two parts that move up and down",
                body = "The spark plug ignites the mixture, the piston slides down, the crankshaft spins, and the valves open and close.",
                items = listOf("piston", "valves"),
                explanation = "The piston and the valves move up and down. The crankshaft spins, and the spark plug doesn't move at all.",
                misconception = "The crankshaft moves, but it rotates rather than sliding up and down.",
                hint = "One of them spins rather than sliding.",
                concept = "Engine motion",
            ),
            LessonStep(
                type = "match", title = "Match each part to its job",
                pairs = listOf(
                    MatchPair("Piston", "Gets pushed by gas"),
                    MatchPair("Crankshaft", "Turns push into rotation"),
                    MatchPair("Cylinder", "Seals in the pressure"),
                    MatchPair("Spark plug", "Starts the burn"),
                ),
                explanation = "Each part has one job in turning pressure into rotation.",
                concept = "Engine parts",
            ),
        ),
    )

    private val fourStroke = LessonContent(
        summary = "Every power pulse takes four strokes and two crankshaft turns.",
        keyPoints = listOf("Intake: draw in air and fuel", "Compression: squeeze it", "Power: ignite and push", "Exhaust: clear it out"),
        steps = listOf(
            LessonStep(
                type = "concept", title = "Suck, squeeze, bang, blow",
                body = "Mechanics remember the four-stroke cycle as suck, squeeze, bang, blow. The piston moves down to suck in air and fuel, up to squeeze it, gets blasted down by the bang, then moves up to blow the exhaust out. Only one stroke in four makes power.",
                visual = Visual("cycle", "The four strokes", items = listOf(
                    VisualItem("Intake", detail = "Piston down, intake valve open"),
                    VisualItem("Compression", detail = "Piston up, both valves closed"),
                    VisualItem("Power", detail = "Spark, piston forced down"),
                    VisualItem("Exhaust", detail = "Piston up, exhaust valve open"),
                )),
                concept = "Four-stroke cycle",
            ),
            LessonStep(
                type = "engine_sim", title = "Run the engine yourself",
                body = "Drag the crank angle and watch the valves, piston and pressure. Find the moment the spark fires.",
                concept = "Four-stroke cycle",
            ),
            LessonStep(
                type = "mcq", title = "During compression…",
                body = "During the compression stroke, what happens to the air-fuel mixture?",
                options = listOf("It's squeezed into a smaller volume", "It expands and cools", "It's ignited", "It leaves through the exhaust"),
                answer = 0,
                explanation = "With both valves closed, the rising piston squeezes the mixture into a fraction of its volume, raising its pressure and temperature.",
                misconception = "Ignition happens at the very END of compression, which starts the power stroke.",
                simpler = "Push a bike pump with your finger over the hole. The air gets squeezed and warm.",
                hint = "Both valves are closed and the piston is moving up.",
                concept = "Compression",
            ),
            LessonStep(type = "engine_label", title = "Drag each part to its place", body = "Label the cylinder.", concept = "Engine parts"),
            LessonStep(
                type = "order", title = "Put the strokes in order",
                body = "Tap the strokes in the order they happen, starting from intake.",
                items = listOf("Intake", "Compression", "Power", "Exhaust"),
                explanation = "Intake, compression, power, exhaust. Suck, squeeze, bang, blow.",
                concept = "Four-stroke cycle",
            ),
            LessonStep(
                type = "fill", title = "Fill in the blank",
                body = "The spark plug fires at the start of the ____ stroke.",
                answerText = "power",
                explanation = "The spark fires near the top of compression, which kicks off the power stroke.",
                misconception = "Many people guess 'compression' because the spark happens right as compression ends.",
                simpler = "Bang comes after squeeze.",
                hint = "It's the 'bang' in suck, squeeze, bang, blow.",
                concept = "Ignition",
            ),
            LessonStep(
                type = "mcq", title = "Scenario",
                body = "A mechanic says an engine has a leaky intake valve that doesn't fully close. Which stroke suffers most?",
                options = listOf("Intake", "Compression", "Exhaust", "None"),
                answer = 1,
                explanation = "If the intake valve leaks, the mixture escapes while the piston tries to squeeze it, so compression (and then power) drops.",
                misconception = "A leaky valve still lets air in during intake. The problem shows up when it should be sealed.",
                simpler = "A leaky bike pump can still suck air in, but it can't build pressure.",
                hint = "When should the intake valve be sealed shut?",
                concept = "Compression",
            ),
        ),
    )
}
