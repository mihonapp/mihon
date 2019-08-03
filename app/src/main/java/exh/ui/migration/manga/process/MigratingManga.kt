package exh.ui.migration.manga.process

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import exh.util.DeferredField
import exh.util.await
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlin.coroutines.CoroutineContext

class MigratingManga(private val db: DatabaseHelper,
                     private val sourceManager: SourceManager,
                     val mangaId: Long,
                     parentContext: CoroutineContext) {
    val searchResult = DeferredField<Long?>()

    // <MAX, PROGRESS>
    val progress = ConflatedBroadcastChannel(1 to 0)

    val migrationJob = parentContext + SupervisorJob() + Dispatchers.Default

    @Volatile
    private var manga: Manga? = null
    suspend fun manga(): Manga? {
        if(manga == null) manga = db.getManga(mangaId).await()
        return manga
    }

    suspend fun mangaSource(): Source {
        return sourceManager.getOrStub(manga()?.source ?: -1)
    }
}