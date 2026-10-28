package eu.kanade.tachiyomi.data.library

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Manga whose latest library update failed. A later successful update clears the mark.
 */
@Inject
@SingleIn(AppScope::class)
class LibraryUpdateErrors(
    preferenceStore: PreferenceStore,
) {
    private val serialized = preferenceStore.getString(
        Preference.appStateKey("library_update_errors"),
        "",
    )

    val errors = serialized

    fun snapshot(): Map<Long, String> = decode(serialized.get())

    fun ids(raw: String): Set<Long> = decode(raw).keys

    fun applyResults(succeeded: Collection<Long>, failed: List<Pair<Long, String?>>) {
        if (succeeded.isEmpty() && failed.isEmpty()) return
        val current = snapshot().toMutableMap()
        succeeded.forEach { current.remove(it) }
        failed.forEach { (mangaId, message) ->
            current[mangaId] = message.orEmpty().replace('\n', ' ').replace('\t', ' ').take(300)
        }
        serialized.set(encode(current))
    }

    private fun decode(raw: String): Map<Long, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val id = line.substring(0, tab).toLongOrNull() ?: return@mapNotNull null
            id to line.substring(tab + 1)
        }.toMap()
    }

    private fun encode(errors: Map<Long, String>): String {
        return errors.entries.joinToString("\n") { (id, message) -> "$id\t$message" }
    }
}
