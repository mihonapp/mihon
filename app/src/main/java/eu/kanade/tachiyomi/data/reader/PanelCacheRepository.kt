package eu.kanade.tachiyomi.data.reader

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelPageData
import kotlinx.serialization.json.Json
import tachiyomi.data.Database

@Inject
@SingleIn(AppScope::class)
class PanelCacheRepository(
    private val database: Database,
    private val json: Json,
) {

    suspend fun get(chapterId: Long, pageIndex: Int, imageHash: String, detectorVersion: Int): PanelPageData? {
        val panelsJson = database.panel_cacheQueries
            .getPanels(chapterId, pageIndex.toLong(), imageHash, detectorVersion.toLong())
            .awaitAsOneOrNull()
            ?: return null
        return runCatching { json.decodeFromString<PanelPageData>(panelsJson) }.getOrNull()
    }

    suspend fun save(chapterId: Long, pageIndex: Int, imageHash: String, detectorVersion: Int, data: PanelPageData) {
        database.panel_cacheQueries.upsert(
            chapterId = chapterId,
            pageIndex = pageIndex.toLong(),
            imageHash = imageHash,
            detectorVersion = detectorVersion.toLong(),
            panelsJson = json.encodeToString(PanelPageData.serializer(), data),
            detectedAt = System.currentTimeMillis(),
        )
    }
}
