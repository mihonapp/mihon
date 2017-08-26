package exh.metadata

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.SManga
import exh.*
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.NHentaiMetadata
import exh.metadata.models.PervEdenGalleryMetadata
import exh.metadata.models.SearchableGalleryMetadata
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import rx.Observable
import timber.log.Timber
import kotlin.reflect.KClass

fun Realm.ehMetaQueryFromUrl(url: String,
                             exh: Boolean,
                             meta: RealmQuery<ExGalleryMetadata>? = null) =
        ehMetadataQuery(
                ExGalleryMetadata.galleryId(url),
                ExGalleryMetadata.galleryToken(url),
                exh,
                meta
        )

fun Realm.ehMetadataQuery(gId: String,
                          gToken: String,
                          exh: Boolean,
                          meta: RealmQuery<ExGalleryMetadata>? = null)
        = (meta ?: where(ExGalleryMetadata::class.java))
        .equalTo(ExGalleryMetadata::gId.name, gId)
        .equalTo(ExGalleryMetadata::gToken.name, gToken)
        .equalTo(ExGalleryMetadata::exh.name, exh)

fun Realm.loadEh(gId: String, gToken: String, exh: Boolean): ExGalleryMetadata?
        = ehMetadataQuery(gId, gToken, exh)
        .findFirst()

fun Realm.loadEhAsync(gId: String, gToken: String, exh: Boolean): Observable<ExGalleryMetadata?>
        = ehMetadataQuery(gId, gToken, exh)
        .findFirstAsync()
        .asObservable()

private fun pervEdenSourceToLang(source: Long)
        = when (source) {
    PERV_EDEN_EN_SOURCE_ID -> "en"
    PERV_EDEN_IT_SOURCE_ID -> "it"
    else -> throw IllegalArgumentException()
}

fun Realm.pervEdenMetaQueryFromUrl(url: String,
                                   source: Long,
                                   meta: RealmQuery<PervEdenGalleryMetadata>? = null) =
        pervEdenMetadataQuery(
                PervEdenGalleryMetadata.pvIdFromUrl(url),
                source,
                meta
        )

fun Realm.pervEdenMetadataQuery(pvId: String,
                                source: Long,
                                meta: RealmQuery<PervEdenGalleryMetadata>? = null)
        = (meta ?: where(PervEdenGalleryMetadata::class.java))
        .equalTo(PervEdenGalleryMetadata::lang.name, pervEdenSourceToLang(source))
        .equalTo(PervEdenGalleryMetadata::pvId.name, pvId)

fun Realm.loadPervEden(pvId: String, source: Long): PervEdenGalleryMetadata?
        = pervEdenMetadataQuery(pvId, source)
        .findFirst()

fun Realm.loadPervEdenAsync(pvId: String, source: Long): Observable<PervEdenGalleryMetadata?>
        = pervEdenMetadataQuery(pvId, source)
        .findFirstAsync()
        .asObservable()

fun Realm.nhentaiMetaQueryFromUrl(url: String,
                                  meta: RealmQuery<NHentaiMetadata>? = null) =
        nhentaiMetadataQuery(
                NHentaiMetadata.nhIdFromUrl(url),
                meta
        )

fun Realm.nhentaiMetadataQuery(nhId: Long,
                               meta: RealmQuery<NHentaiMetadata>? = null)
        = (meta ?: where(NHentaiMetadata::class.java))
        .equalTo(NHentaiMetadata::nhId.name, nhId)

fun Realm.loadNhentai(nhId: Long): NHentaiMetadata?
        = nhentaiMetadataQuery(nhId)
        .findFirst()

fun Realm.loadNhentaiAsync(nhId: Long): Observable<NHentaiMetadata?>
        = nhentaiMetadataQuery(nhId)
        .findFirstAsync()
        .asObservable()

fun Realm.loadAllMetadata(): Map<KClass<out SearchableGalleryMetadata>, RealmResults<out SearchableGalleryMetadata>> =
        listOf<Pair<KClass<out SearchableGalleryMetadata>, RealmQuery<out SearchableGalleryMetadata>>>(
                Pair(ExGalleryMetadata::class, where(ExGalleryMetadata::class.java)),
                Pair(NHentaiMetadata::class, where(NHentaiMetadata::class.java)),
                Pair(PervEdenGalleryMetadata::class, where(PervEdenGalleryMetadata::class.java))
        ).map {
            Pair(it.first, it.second.findAllSorted(SearchableGalleryMetadata::mangaId.name))
        }.toMap()

fun Realm.queryMetadataFromManga(manga: Manga,
                                 meta: RealmQuery<out SearchableGalleryMetadata>? = null): RealmQuery<out SearchableGalleryMetadata> =
    when(manga.source) {
        EH_SOURCE_ID -> ehMetaQueryFromUrl(manga.url, false, meta as? RealmQuery<ExGalleryMetadata>)
        EXH_SOURCE_ID -> ehMetaQueryFromUrl(manga.url, true, meta as? RealmQuery<ExGalleryMetadata>)
        PERV_EDEN_EN_SOURCE_ID,
        PERV_EDEN_IT_SOURCE_ID ->
            pervEdenMetaQueryFromUrl(manga.url, manga.source, meta as? RealmQuery<PervEdenGalleryMetadata>)
        NHENTAI_SOURCE_ID -> nhentaiMetaQueryFromUrl(manga.url, meta as? RealmQuery<NHentaiMetadata>)
        else -> throw IllegalArgumentException("Unknown source type!")
    }

fun Realm.syncMangaIds(mangas: List<Manga>) {
    Timber.d("--> EH: Begin syncing ${mangas.size} manga IDs...")
    executeTransaction {
        mangas.filter {
            isLewdSource(it.source)
        }.forEach { manga ->
            try {
                queryMetadataFromManga(manga).findFirst()?.let { meta ->
                    meta.mangaId = manga.id
                }
            } catch(e: Exception) {
                Timber.w(e, "Error syncing manga IDs! Ignoring...")
            }
        }
    }
    Timber.d("--> EH: Finish syncing ${mangas.size} manga IDs!")
}