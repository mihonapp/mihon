package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.models.GalleryQuery
import exh.metadata.models.SearchableGalleryMetadata
import exh.util.createUUIDObj
import exh.util.defRealm
import exh.util.realmTrans
import rx.Observable

/**
 * LEWD!
 */
interface LewdSource<M : SearchableGalleryMetadata, I> : CatalogueSource {
    fun queryAll(): GalleryQuery<M>

    fun queryFromUrl(url: String): GalleryQuery<M>

    val metaParser: M.(I) -> Unit

    fun parseToManga(query: GalleryQuery<M>, input: I): SManga
            = realmTrans { realm ->
        val meta = realm.copyFromRealm(query.query(realm).findFirst()
                ?: realm.createUUIDObj(queryAll().clazz.java))

        metaParser(meta, input)

        realm.copyToRealmOrUpdate(meta)

        SManga.create().apply {
            meta.copyTo(this)
        }
    }

    fun lazyLoadMeta(query: GalleryQuery<M>, parserInput: Observable<I>): Observable<M> {
        return defRealm { realm ->
            val possibleOutput = query.query(realm).findFirst()

            if(possibleOutput == null)
                parserInput.map {
                    realmTrans { realm ->
                        val meta = realm.createUUIDObj(queryAll().clazz.java)

                        metaParser(meta, it)

                        realm.copyFromRealm(meta)
                    }
                }
            else
                Observable.just(realm.copyFromRealm(possibleOutput))
        }
    }
}
