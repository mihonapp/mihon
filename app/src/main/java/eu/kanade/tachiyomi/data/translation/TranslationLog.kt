package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.translation.overlay.TextBox

/**
 * Collects a step by step log of one translation run. The worker writes it to a text file in the Local source folder
 * so that a failed or badly translated chapter can be diagnosed afterwards.
 *
 * Translations run one chapter at a time, so a single process wide log is enough. Every call does nothing while no
 * log is active, and messages are only built when one is.
 *
 * The API key is removed from everything that is logged.
 */
object TranslationLog {

    @Volatile
    private var buffer: TranslationLogBuffer? = null

    val isActive: Boolean get() = buffer != null

    fun start(secrets: List<String>) {
        buffer = TranslationLogBuffer(secrets)
    }

    fun stop() {
        buffer = null
    }

    /** Everything logged so far, or null when no log is active. */
    fun text(): String? = buffer?.text()

    /** Adds one step. [message] is only built while a log is active. */
    fun step(message: () -> String) {
        buffer?.add(message())
    }

    fun error(message: String, error: Throwable) {
        buffer?.add("$message\n${describe(error)}")
    }

    /** The class and message of [error] and its causes, with the first few stack frames of each. */
    fun describe(error: Throwable, maxFrames: Int = DEFAULT_STACK_FRAMES): String {
        val result = StringBuilder()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (depth > 0) result.append("caused by ")
            result.append(current.javaClass.simpleName.ifEmpty { current.javaClass.name })
            current.message?.let { result.append(": ").append(clip(it, MAX_MESSAGE_CHARS)) }
            result.append('\n')
            current.stackTrace.take(maxFrames).forEach { result.append("    at ").append(it).append('\n') }
            val cause = current.cause
            current = if (cause === current) null else cause
            depth++
        }
        return result.toString().trimEnd()
    }

    /** One text box on its 0-1000 grid, with its transcription and translation. */
    fun describe(box: TextBox): String =
        "${box.kind.name.lowercase()} [y ${box.yMin}-${box.yMax}, x ${box.xMin}-${box.xMax}] " +
            "source \"${clip(box.sourceText)}\" -> \"${clip(box.text)}\""

    /** [text] on one line and at most [max] characters. */
    fun clip(text: String, max: Int = DEFAULT_CLIP): String {
        val flat = text.replace(WHITESPACE, " ").trim()
        return if (flat.length <= max) flat else flat.take(max) + "…"
    }

    private const val DEFAULT_CLIP = 160
    private const val MAX_MESSAGE_CHARS = 300
    private const val DEFAULT_STACK_FRAMES = 6
    private const val MAX_CAUSE_DEPTH = 4
    private val WHITESPACE = Regex("\\s+")
}

/**
 * The text of one log. Every line starts with the time since the log was started, which shows how long each step
 * took. Anything in [secrets] is replaced before it is stored, and the log stops growing at [maxChars].
 */
class TranslationLogBuffer(
    private val secrets: List<String> = emptyList(),
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val startMillis = clock()
    private val builder = StringBuilder()
    private var full = false

    @Synchronized
    fun add(message: String) {
        if (full) return
        val prefix = "[${formatElapsed(clock() - startMillis)}] "
        val text = redact(message).replace("\n", "\n" + " ".repeat(prefix.length))
        if (builder.length + prefix.length + text.length + 1 > maxChars) {
            full = true
            builder.append(prefix).append("(the log is full, later steps are not recorded)\n")
            return
        }
        builder.append(prefix).append(text).append('\n')
    }

    @Synchronized
    fun text(): String = builder.toString()

    private fun redact(message: String): String {
        var result = message
        for (secret in secrets) {
            if (secret.length >= MIN_SECRET_LENGTH) result = result.replace(secret, "***")
        }
        return result
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 1_500_000
        private const val MIN_SECRET_LENGTH = 8
        private const val MILLIS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60L

        /** `mm:ss.mmm`, with digits that do not depend on the device language. */
        internal fun formatElapsed(millis: Long): String {
            val clamped = millis.coerceAtLeast(0)
            val totalSeconds = clamped / MILLIS_PER_SECOND
            val minutes = (totalSeconds / SECONDS_PER_MINUTE).toString().padStart(2, '0')
            val seconds = (totalSeconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')
            val rest = (clamped % MILLIS_PER_SECOND).toString().padStart(3, '0')
            return "$minutes:$seconds.$rest"
        }
    }
}
