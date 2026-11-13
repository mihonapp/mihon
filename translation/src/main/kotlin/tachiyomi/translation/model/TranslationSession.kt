package tachiyomi.translation.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * In-memory session: at most [maxSpreads] spreads, oldest evicted first, no persistence to disk.
 *
 * Deliberately not thread safe; callers drive it from the reader's main dispatcher.
 */
class TranslationSession(
    private val maxSpreads: Int = MAX_SPREADS,
) {
    private val spreads = LinkedHashMap<SpreadKey, MutableList<TranslationEntry>>()
    private var nextEntryId = 1L
    private var nextGroupId = 1L

    fun newGroupId(): Long = nextGroupId++

    fun entriesOf(spread: SpreadKey): List<TranslationEntry> = spreads[spread].orEmpty().toList()

    fun activeSpreads(): List<SpreadKey> = spreads.keys.toList()

    fun hasSpread(spread: SpreadKey): Boolean = spreads.containsKey(spread)

    fun add(
        spread: SpreadKey,
        page: PageKey,
        rect: NormalizedRect,
        groupId: Long? = null,
    ): TranslationEntry {
        touch(spread)
        val list = spreads.getOrPut(spread) { mutableListOf() }
        val entry = TranslationEntry(
            id = nextEntryId++,
            seq = list.size + 1,
            chapterId = page.chapterId,
            pageIndex = page.pageIndex,
            side = page.side,
            rect = rect.clampToUnit(),
            groupId = groupId,
        )
        list += entry
        evictIfNeeded()
        return entry
    }

    fun find(entryId: Long): TranslationEntry? = spreads.values
        .asSequence()
        .flatten()
        .firstOrNull { it.id == entryId }

    fun update(entryId: Long, transform: (TranslationEntry) -> TranslationEntry): TranslationEntry? {
        spreads.values.forEach { list ->
            val index = list.indexOfFirst { it.id == entryId }
            if (index >= 0) {
                return transform(list[index]).also { list[index] = it }
            }
        }
        return null
    }

    fun remove(entryId: Long): Boolean {
        spreads.entries.forEach { (spread, list) ->
            val index = list.indexOfFirst { it.id == entryId }
            if (index >= 0) {
                list.removeAt(index)
                if (list.isEmpty()) spreads.remove(spread) else renumber(list)
                return true
            }
        }
        return false
    }

    fun removeSpread(spread: SpreadKey) {
        spreads.remove(spread)
    }

    fun clear() {
        spreads.clear()
    }

    fun snapshot(): SessionSnapshot = SessionSnapshot(
        pages = spreads.map { (spread, entries) ->
            SpreadSnapshot(
                chapterId = spread.chapterId,
                anchorIndex = spread.anchorIndex,
                entries = entries.toList(),
            )
        },
        nextEntryId = nextEntryId,
        nextGroupId = nextGroupId,
    )

    fun restore(snapshot: SessionSnapshot) {
        spreads.clear()
        snapshot.pages.takeLast(maxSpreads).forEach { page ->
            spreads[SpreadKey(page.chapterId, page.anchorIndex)] = page.entries.toMutableList()
        }
        nextEntryId = snapshot.nextEntryId.coerceAtLeast(1L)
        nextGroupId = snapshot.nextGroupId.coerceAtLeast(1L)
    }

    /** Re-touches a spread so it is not the next one evicted. */
    private fun touch(spread: SpreadKey) {
        spreads.remove(spread)?.let { spreads[spread] = it }
    }

    private fun evictIfNeeded() {
        while (spreads.size > maxSpreads) {
            val oldest = spreads.keys.firstOrNull() ?: return
            spreads.remove(oldest)
        }
    }

    private fun renumber(list: MutableList<TranslationEntry>) {
        list.indices.forEach { i -> list[i] = list[i].copy(seq = i + 1) }
    }

    companion object {
        const val MAX_SPREADS = 10

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun toJson(snapshot: SessionSnapshot): String = json.encodeToString(SessionSnapshot.serializer(), snapshot)

        fun fromJson(raw: String?): SessionSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString(SessionSnapshot.serializer(), raw) }.getOrNull()
        }
    }
}

@Serializable
data class SessionSnapshot(
    val pages: List<SpreadSnapshot> = emptyList(),
    val nextEntryId: Long = 1L,
    val nextGroupId: Long = 1L,
)

@Serializable
data class SpreadSnapshot(
    val chapterId: Long,
    val anchorIndex: Int,
    val entries: List<TranslationEntry> = emptyList(),
)
