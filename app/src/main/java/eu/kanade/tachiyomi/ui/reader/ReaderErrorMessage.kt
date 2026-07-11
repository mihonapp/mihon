package eu.kanade.tachiyomi.ui.reader

internal fun Throwable.toReadableReaderError(
    isHttpSource: Boolean,
    sourceExtensionMessage: String,
): Throwable {
    val hasNullPointerFailure = generateSequence(this) { it.cause }.any { throwable ->
        throwable is NullPointerException ||
            throwable.message.orEmpty().contains("NullPointerException", ignoreCase = true)
    }

    if (!isHttpSource || !hasNullPointerFailure) {
        return this
    }

    return IllegalStateException(sourceExtensionMessage, this)
}
