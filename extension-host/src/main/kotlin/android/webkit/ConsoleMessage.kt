package android.webkit

class ConsoleMessage(
    private val text: String,
    private val source: String,
    private val line: Int,
    private val level: MessageLevel,
) {
    enum class MessageLevel { TIP, LOG, WARNING, ERROR, DEBUG }
    fun message(): String = text
    fun sourceId(): String = source
    fun lineNumber(): Int = line
    fun messageLevel(): MessageLevel = level
}
