package tachiyomi.core.common.util.system

import logcat.LogPriority
import logcat.asLog
import logcat.logcat

inline fun Any.logcat(
    priority: LogPriority = LogPriority.DEBUG,
    throwable: Throwable? = null,
    tag: String? = null,
    message: () -> String = { "" },
) = logcat(priority = priority) {
    val logMessage = StringBuilder()

    if (!tag.isNullOrEmpty()) {
        logMessage.append("[$tag] ")
    }

    val msg = message()
    logMessage.append(msg)

    if (throwable != null) {
        if (msg.isNotBlank()) logMessage.append("\n")
        logMessage.append(throwable.asLog())
    }

    logMessage.toString()
}
