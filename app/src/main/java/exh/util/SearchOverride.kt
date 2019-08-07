package exh.util

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import exh.GalleryAddEvent
import exh.GalleryAdder
import rx.Observable

private val galleryAdder by lazy {
    GalleryAdder()
}

/**
 * A version of fetchSearchManga that supports URL importing
 */
fun UrlImportableSource.urlImportFetchSearchManga(query: String, fail: () -> Observable<MangasPage>) =
        when {
            query.startsWith("http://") || query.startsWith("https://") -> {
                Observable.fromCallable {
                    val res = galleryAdder.addGallery(query, false, this)
                    MangasPage((if(res is GalleryAddEvent.Success)
                        listOf(res.manga)
                    else
                        emptyList()), false)
                }
            }
            else -> fail()
        }
