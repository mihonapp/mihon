package exh.metadata

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.ui.library.LibraryItem
import exh.*
import exh.metadata.models.*
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.reflect.KClass

fun Realm.loadAllMetadata(): Map<KClass<out SearchableGalleryMetadata>, RealmResults<out SearchableGalleryMetadata>> =
        Injekt.get<SourceManager>().getOnlineSources().filterIsInstance<LewdSource<*, *>>().map {
            it.queryAll()
        }.associate {
            it.clazz to it.query(this@loadAllMetadata).sort(SearchableGalleryMetadata::mangaId.name).findAll()
        }.toMap()

fun Realm.queryMetadataFromManga(manga: Manga,
                                 meta: RealmQuery<SearchableGalleryMetadata>? = null):
        RealmQuery<out SearchableGalleryMetadata> =
        Injekt.get<SourceManager>().get(manga.source)?.let {
            (it as LewdSource<*, *>).queryFromUrl(manga.url) as GalleryQuery<SearchableGalleryMetadata>
        }?.query(this, meta) ?: throw IllegalArgumentException("Unknown source type!")

fun Realm.syncMangaIds(mangas: List<LibraryItem>) {
    Timber.d("--> EH: Begin syncing ${mangas.size} manga IDs...")
    executeTransaction {
        mangas.forEach { manga ->
            if(isLewdSource(manga.manga.source)) {
                try {
                    manga.hasMetadata =
                            queryMetadataFromManga(manga.manga).findFirst()?.let { meta ->
                                meta.mangaId = manga.manga.id
                                true
                            } ?: false
                } catch (e: Exception) {
                    Timber.w(e, "Error syncing manga IDs! Ignoring...")
                }
            }
        }
    }
    Timber.d("--> EH: Finish syncing ${mangas.size} manga IDs!")
}

val Manga.metadataClass
    get() = (Injekt.get<SourceManager>().get(source) as? LewdSource<*, *>)?.queryAll()?.clazz
