package eu.kanade.tachiyomi.ui.migration.manga.process

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.DeferredField
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ConflatedBroadcastChannel

class MigratingManga(
    private val db: DatabaseHelper,
    private val sourceManager: SourceManager,
    val mangaId: Long,
    parentContext: CoroutineContext
) {
    val searchResult = DeferredField<Long?>()

    // <MAX, PROGRESS>
    val progress = ConflatedBroadcastChannel(1 to 0)

    val migrationJob = parentContext + SupervisorJob() + Dispatchers.Default

    @Volatile
    private var manga: Manga? = null
    suspend fun manga(): Manga? {
        if (manga == null) manga = db.getManga(mangaId).executeAsBlocking()
        return manga
    }

    suspend fun mangaSource(): Source {
        return sourceManager.getOrStub(manga()?.source ?: -1)
    }

    fun toModal(): MigrationProcessItem {
        // Create the model object.
        return MigrationProcessItem(this)
    }
}
