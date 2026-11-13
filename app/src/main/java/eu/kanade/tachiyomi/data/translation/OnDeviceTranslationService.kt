package eu.kanade.tachiyomi.data.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.domain.translation.model.TranslationLanguage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Translates with the model Google ships for on-device use, so pages keep being translated without a connection.
 *
 * Each language pair downloads its model once (tens of megabytes) and then works offline. The first page of a new
 * pair therefore needs a connection.
 */
class OnDeviceTranslationService : TranslationService {

    override suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (source == TranslationLanguage.AUTO) {
            // The recognizer already knows which script it just read; the caller resolves it before getting here.
            throw TranslationException(TranslationError.SOURCE_LANGUAGE_REQUIRED)
        }

        val from = source.mlKitCode() ?: throw TranslationException(
            TranslationError.UNSUPPORTED_SOURCE_SCRIPT,
            source.code,
        )
        val to = target.mlKitCode() ?: throw TranslationException(
            TranslationError.UNSUPPORTED_SOURCE_SCRIPT,
            target.code,
        )

        val client = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(from)
                .setTargetLanguage(to)
                .build(),
        )

        try {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
            return texts.map { text ->
                if (text.isBlank()) text else client.translate(text).awaitResult()
            }
        } finally {
            client.close()
        }
    }

    /**
     * ML Kit numbers its languages with two letter codes; our languages carry the script subtag as well
     * ("zh-Hans"), which the library doesn't know.
     */
    private fun TranslationLanguage.mlKitCode(): String? = TranslateLanguage.fromLanguageTag(code.substringBefore('-'))

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
}
