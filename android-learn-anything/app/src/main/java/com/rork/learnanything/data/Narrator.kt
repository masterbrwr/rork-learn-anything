package com.rork.learnanything.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * One spoken piece of a script. [part] + [index] let the UI highlight what is being read
 * (a sentence of the body, an answer option, the feedback...).
 */
data class Segment(val text: String, val part: String, val index: Int = 0)

/**
 * App-wide narrator built on Android's on-device text-to-speech.
 * Free, offline-capable, and uses the best installed voice. Sentence-by-sentence queueing gives
 * live highlighting, instant skip back / forward, pause & resume, and speed changes mid-sentence.
 */
object Narrator {
    enum class Status { Idle, Preparing, Playing, Paused }

    data class State(
        val status: Status = Status.Idle,
        val key: String? = null,
        val segments: List<Segment> = emptyList(),
        val index: Int = 0,
        val wordStart: Int = -1,
        val wordEnd: Int = -1,
        val unavailable: Boolean = false,
    ) {
        val current: Segment? get() = segments.getOrNull(index)
        val active: Boolean get() = status == Status.Playing || status == Status.Preparing
        val progress: Float get() = if (segments.isEmpty()) 0f else (index + if (status == Status.Idle && key != null) 1f else 0f) / segments.size
    }

    data class VoiceOption(val id: String, val label: String, val detail: String, val quality: Int, val online: Boolean)

    val state = MutableStateFlow(State())
    val voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    /** Emits the key of a script that played through to the end (not stopped / paused). */
    val completed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    private var app: Context? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initializing = false
    private var pending: (() -> Unit)? = null
    private var generation = 0
    private var rate = 1f
    private var voiceId = ""
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
    }

    fun init(context: Context) {
        app = context.applicationContext
        val s = AppStore.state.value
        rate = s.narratorRate
        voiceId = s.narratorVoice
    }

    private fun ensureEngine(then: () -> Unit) {
        if (ready && tts != null) { then(); return }
        pending = then
        if (initializing) return
        val ctx = app ?: return
        initializing = true
        state.value = state.value.copy(status = Status.Preparing, unavailable = false)
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx) { status ->
            initializing = false
            val e = engine
            if (status != TextToSpeech.SUCCESS || e == null) {
                state.value = state.value.copy(status = Status.Idle, unavailable = true)
                pending = null
                return@TextToSpeech
            }
            val lang = e.setLanguage(Locale.getDefault())
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                val us = e.setLanguage(Locale.US)
                if (us == TextToSpeech.LANG_MISSING_DATA || us == TextToSpeech.LANG_NOT_SUPPORTED) {
                    state.value = state.value.copy(status = Status.Idle, unavailable = true)
                    pending = null
                    return@TextToSpeech
                }
            }
            e.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            e.setOnUtteranceProgressListener(listener)
            tts = e
            ready = true
            loadVoices()
            applyVoice()
            e.setSpeechRate(rate)
            e.setPitch(1f)
            pending?.invoke(); pending = null
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (gen, i) = parse(utteranceId) ?: return
            if (gen != generation) return
            state.value = state.value.copy(status = Status.Playing, index = i, wordStart = -1, wordEnd = -1)
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (gen, i) = parse(utteranceId) ?: return
            if (gen != generation) return
            state.value = state.value.copy(index = i, wordStart = start, wordEnd = end)
        }

        override fun onDone(utteranceId: String?) {
            val (gen, i) = parse(utteranceId) ?: return
            if (gen != generation) return
            val st = state.value
            if (i >= st.segments.lastIndex) {
                state.value = st.copy(status = Status.Idle, index = st.segments.lastIndex.coerceAtLeast(0), wordStart = -1, wordEnd = -1)
                abandonFocus()
                st.key?.let { completed.tryEmit(it) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val (gen, _) = parse(utteranceId) ?: return
            if (gen != generation) return
            state.value = state.value.copy(status = Status.Idle)
            abandonFocus()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            @Suppress("DEPRECATION") onError(utteranceId)
        }
    }

    private fun parse(id: String?): Pair<Int, Int>? {
        val p = id?.split(":") ?: return null
        if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) to (p[1].toIntOrNull() ?: return null)
    }

    /** Play [segments] under [key], starting at [from]. Replaces whatever is playing. */
    fun play(key: String, segments: List<Segment>, from: Int = 0) {
        val clean = segments.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return
        generation++
        tts?.stop()
        state.value = state.value.copy(status = Status.Preparing, key = key, segments = clean, index = from.coerceIn(0, clean.lastIndex), wordStart = -1, wordEnd = -1)
        ensureEngine { speakFrom(state.value.index) }
    }

    private fun speakFrom(from: Int) {
        val e = tts ?: return
        val st = state.value
        if (st.segments.isEmpty()) return
        generation++
        val gen = generation
        e.stop()
        requestFocus()
        state.value = st.copy(status = Status.Preparing, index = from, wordStart = -1, wordEnd = -1)
        st.segments.forEachIndexed { i, seg ->
            if (i < from) return@forEachIndexed
            e.speak(seg.text, TextToSpeech.QUEUE_ADD, null, "$gen:$i")
            // A short breath between the prompt and its answer options reads more naturally.
            if (seg.part != st.segments.getOrNull(i + 1)?.part) e.playSilentUtterance(260, TextToSpeech.QUEUE_ADD, "s$gen:$i")
        }
    }

    fun pause() {
        val st = state.value
        if (!st.active) return
        generation++
        tts?.stop()
        abandonFocus()
        state.value = st.copy(status = Status.Paused, wordStart = -1, wordEnd = -1)
    }

    fun resume() {
        val st = state.value
        if (st.status != Status.Paused || st.segments.isEmpty()) return
        ensureEngine { speakFrom(state.value.index) }
    }

    /** Jump [delta] segments (negative = back). Keeps the play/pause state. */
    fun skip(delta: Int) {
        val st = state.value
        if (st.segments.isEmpty()) return
        val target = (st.index + delta).coerceIn(0, st.segments.lastIndex)
        if (st.active) ensureEngine { speakFrom(target) }
        else state.value = st.copy(index = target, status = if (st.status == Status.Idle) Status.Paused else st.status)
    }

    fun stop() {
        generation++
        tts?.stop()
        abandonFocus()
        state.value = State(unavailable = state.value.unavailable)
    }

    fun setRate(r: Float) {
        rate = r
        AppStore.update { it.copy(narratorRate = r) }
        tts?.setSpeechRate(r)
        if (state.value.active) ensureEngine { speakFrom(state.value.index) }
    }

    fun setVoice(id: String) {
        voiceId = id
        AppStore.update { it.copy(narratorVoice = id) }
        applyVoice()
        if (state.value.active) ensureEngine { speakFrom(state.value.index) }
    }

    /** Short sample for the voice picker / speed setting. */
    fun preview(id: String? = null) {
        if (id != null && id != voiceId) setVoice(id)
        play("preview", listOf(Segment("Hi. I'll read your lessons out loud, so you can learn with your eyes closed.", "preview")))
    }

    fun warmUp() = ensureEngine { }

    private fun applyVoice() {
        val e = tts ?: return
        val all = runCatching { e.voices }.getOrNull().orEmpty()
        val chosen = all.firstOrNull { it.name == voiceId } ?: bestVoice(all)
        chosen?.let { runCatching { e.voice = it } }
    }

    private fun usable(v: Voice) = !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    private fun bestVoice(all: Collection<Voice>): Voice? {
        val loc = Locale.getDefault()
        return all.filter { usable(it) && it.locale.language == loc.language && !it.isNetworkConnectionRequired }
            .sortedWith(compareByDescending<Voice> { it.locale.country == loc.country }.thenByDescending { it.quality }.thenBy { it.latency })
            .firstOrNull()
    }

    private fun loadVoices() {
        val e = tts ?: return
        val loc = Locale.getDefault()
        val all = runCatching { e.voices }.getOrNull().orEmpty()
            .filter { usable(it) && it.locale.language == loc.language }
            .sortedWith(
                compareByDescending<Voice> { it.locale.country == loc.country }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
        val counters = mutableMapOf<String, Int>()
        voices.value = all.take(24).map { v ->
            val region = v.locale.getDisplayCountry(loc).ifBlank { v.locale.getDisplayLanguage(loc) }
            val n = (counters[region] ?: 0) + 1
            counters[region] = n
            val q = when {
                v.quality >= Voice.QUALITY_VERY_HIGH -> "Studio quality"
                v.quality >= Voice.QUALITY_HIGH -> "High quality"
                else -> "Standard"
            }
            VoiceOption(
                id = v.name,
                label = "$region · Voice $n",
                detail = listOfNotNull(q, if (v.isNetworkConnectionRequired) "Needs internet" else "Works offline").joinToString(" · "),
                quality = v.quality,
                online = v.isNetworkConnectionRequired,
            )
        }
    }

    fun currentVoiceId(): String = voiceId.ifBlank { tts?.voice?.name.orEmpty() }

    private fun requestFocus() {
        val am = app?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(focusListener)
                .build().also { focusRequest = it }
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonFocus() {
        val am = app?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { am.abandonAudioFocusRequest(it) }
        else @Suppress("DEPRECATION") am.abandonAudioFocus(focusListener)
    }
}

// ---------------------------------------------------------------------------------------------
// Scripts: turn lesson steps into natural spoken text.
// ---------------------------------------------------------------------------------------------

/** Splits raw (markdown-ish) text into sentences, returning each sentence with the whitespace that followed it. */
fun splitSentences(raw: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    var start = 0
    var i = 0
    val n = raw.length
    while (i < n) {
        val ch = raw[i]
        val boundary = when {
            ch == '\n' -> true
            (ch == '.' || ch == '!' || ch == '?') && i + 1 < n && raw[i + 1].isWhitespace() -> {
                var j = i + 1
                while (j < n && raw[j] == ' ') j++
                j >= n || raw[j] == '\n' || raw[j].isUpperCase() || raw[j].isDigit() || raw[j] in "\"“(*=-"
            }
            else -> false
        }
        if (boundary) {
            val endSentence = if (ch == '\n') i else i + 1
            var j = endSentence
            while (j < n && raw[j].isWhitespace()) j++
            val sentence = raw.substring(start, endSentence)
            if (sentence.isNotBlank()) out += sentence to raw.substring(endSentence, j)
            else if (out.isNotEmpty()) out[out.lastIndex] = out.last().first to (out.last().second + raw.substring(start, j))
            start = j
            i = j
        } else i++
    }
    if (start < n && raw.substring(start).isNotBlank()) out += raw.substring(start) to ""
    return out
}

private val SYMBOLS = listOf(
    "→" to " leads to ", "->" to " leads to ", "←" to " comes from ", "⇒" to " implies ",
    "×" to " times ", "÷" to " divided by ", "≈" to " approximately ", "≠" to " is not equal to ",
    "≤" to " is less than or equal to ", "≥" to " is greater than or equal to ", "±" to " plus or minus ",
    "√" to " the square root of ", "π" to " pi ", "∞" to " infinity ", "Δ" to " delta ", "°C" to " degrees Celsius",
    "°F" to " degrees Fahrenheit", "°" to " degrees", "²" to " squared", "³" to " cubed", "^2" to " squared",
    "^3" to " cubed", "e.g." to "for example", "i.e." to "that is", "etc." to "and so on", "vs." to "versus",
    " & " to " and ",
)

/** Strips markdown and turns symbols into words, so the voice reads naturally. */
fun speakable(raw: String): String {
    var t = raw.replace("**", "").replace("==", "").replace("`", "").replace("__", "")
    t = t.replace(Regex("(?m)^\\s*#+\\s*"), "").replace(Regex("(?m)^\\s*[-*•]\\s+"), "")
    t = t.replace(Regex("_{3,}"), " blank ")
    SYMBOLS.forEach { (a, b) -> t = t.replace(a, b) }
    t = t.replace(Regex("(\\d)\\s*/\\s*(\\d)"), "$1 over $2")
    t = t.replace(Regex("\\s*=\\s*"), " equals ")
    return t.replace(Regex("\\s+"), " ").trim()
}

private fun sentences(raw: String, part: String): List<Segment> =
    splitSentences(raw).mapIndexed { i, (s, _) -> Segment(speakable(s), part, i) }.filter { it.text.isNotBlank() }

/** What the narrator says for a step before it is answered (or for reading steps). */
fun LessonStep.narration(): List<Segment> {
    val out = mutableListOf<Segment>()
    when (type) {
        "concept", "example", "engine_sim" -> {
            val head = if (type == "example") "Example. ${speakable(title)}" else speakable(title)
            if (head.isNotBlank()) out += Segment(head, "title")
            out += sentences(body, "body")
            visual?.let { v ->
                val cap = listOf(v.title, v.caption).filter { it.isNotBlank() }.joinToString(". ")
                if (cap.isNotBlank()) out += Segment("In the diagram: ${speakable(cap)}", "visual")
                if (v.formula.isNotBlank()) out += Segment("The formula is ${speakable(v.formula)}.", "visual")
            }
        }
        "engine_label" -> {
            out += Segment(speakable(title.ifBlank { "Drag each part to its place" }), "title")
            if (body.isNotBlank()) out += sentences(body, "body")
        }
        "reveal" -> {
            if (title.isNotBlank()) out += Segment(speakable(title), "title")
            out += sentences(body, "body")
            out += Segment("Guess what's on the back of each card, then tap to flip it.", "cue")
        }
        "tapwords" -> {
            if (title.isNotBlank()) out += Segment(speakable(title) + ".", "title")
            out += sentences(body, "body")
        }
        else -> {
            out += sentences(body, "body")
            when (type) {
                "mcq" -> {
                    out += Segment("Your options are:", "cue")
                    options.forEachIndexed { i, o -> out += Segment("${'A' + i}. ${speakable(o)}.", "option", i) }
                }
                "truefalse" -> out += Segment("True, or false?", "cue")
                "fill" -> out += Segment("Type the missing word.", "cue")
                "match" -> {
                    out += Segment("Match each item on the left with its partner.", "cue")
                    pairs.forEachIndexed { i, p -> out += Segment(speakable(p.left) + ".", "option", i) }
                }
                "order" -> {
                    out += Segment("Put these in the right order:", "cue")
                    items.forEachIndexed { i, it -> out += Segment(speakable(it) + ".", "option", i) }
                }
                "estimate" -> out += Segment("Slide to make your best guess.", "cue")
                "sort" -> {
                    out += Segment("Sort each item into ${options.joinToString(" or ") { speakable(it) }}.", "cue")
                    pairs.forEachIndexed { i, p -> out += Segment(speakable(p.left) + ".", "option", i) }
                }
            }
        }
    }
    return out
}

/** What the narrator says after an answer is checked. */
fun LessonStep.feedbackNarration(correct: Boolean, correctAnswer: String): List<Segment> {
    val out = mutableListOf<Segment>()
    out += Segment(if (correct) "Exactly right." else "Not quite. The correct answer is ${speakable(correctAnswer)}.", "feedback", 0)
    if (!correct && misconception.isNotBlank()) out += sentences(misconception, "feedback")
    if (explanation.isNotBlank()) out += sentences(explanation, "feedback")
    if (!correct && simpler.isNotBlank()) out += Segment("Here's a simpler way to see it. ${speakable(simpler)}", "feedback")
    return out
}
