package exh.favorites

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.metadata.models.ExGalleryMetadata
import exh.util.trans
import io.realm.Realm
import io.realm.RealmConfiguration
import uy.kohesive.injekt.injectLazy

class LocalFavoritesStorage {
    private val db: DatabaseHelper by injectLazy()

    private val realmConfig = RealmConfiguration.Builder()
            .name("fav-sync")
            .deleteRealmIfMigrationNeeded()
            .build()

    private val realm
        get() = Realm.getInstance(realmConfig)

    fun getChangedDbEntries()
            = getChangedEntries(
            parseToFavoriteEntries(
                    loadDbCategories(
                            db.getFavoriteMangas()
                                    .executeAsBlocking()
                                    .asSequence()
                    )
            )
    )

    fun getChangedRemoteEntries(entries: List<EHentai.ParsedManga>)
            = getChangedEntries(
            parseToFavoriteEntries(
                    entries.asSequence().map {
                        Pair(it.fav, it.manga.apply {
                            favorite = true
                        })
                    }
            )
    )

    fun snapshotEntries() {
        val dbMangas = parseToFavoriteEntries(
                loadDbCategories(
                        db.getFavoriteMangas()
                                .executeAsBlocking()
                                .asSequence()
                )
        )

        realm.use { realm ->
            realm.trans {
                //Delete old snapshot
                realm.delete(FavoriteEntry::class.java)

                //Insert new snapshots
                realm.copyToRealm(dbMangas.toList())
            }
        }
    }

    fun clearSnapshots() {
        realm.use {
            it.trans {
                it.delete(FavoriteEntry::class.java)
            }
        }
    }

    private fun getChangedEntries(entries: Sequence<FavoriteEntry>): ChangeSet {
        return realm.use { realm ->
            val terminated = entries.toList()

            val added = terminated.filter {
                realm.queryRealmForEntry(it) == null
            }

            val removed = realm.where(FavoriteEntry::class.java)
                    .findAll()
                    .filter {
                        queryListForEntry(terminated, it) == null
                    }.map {
                        realm.copyFromRealm(it)
                    }

            ChangeSet(added, removed)
        }
    }

    private fun Realm.queryRealmForEntry(entry: FavoriteEntry)
            = where(FavoriteEntry::class.java)
            .equalTo(FavoriteEntry::gid.name, entry.gid)
            .equalTo(FavoriteEntry::token.name, entry.token)
            .equalTo(FavoriteEntry::category.name, entry.category)
            .findFirst()

    private fun queryListForEntry(list: List<FavoriteEntry>, entry: FavoriteEntry)
        = list.find {
        it.gid == entry.gid
                && it.token == entry.token
                && it.category == entry.category
    }

    private fun loadDbCategories(manga: Sequence<Manga>): Sequence<Pair<Int, Manga>> {
        val dbCategories = db.getCategories().executeAsBlocking()

        return manga.filter(this::validateDbManga).mapNotNull {
            val category = db.getCategoriesForManga(it).executeAsBlocking()

            Pair(dbCategories.indexOf(category.firstOrNull()
                    ?: return@mapNotNull null), it)
        }
    }

    private fun parseToFavoriteEntries(manga: Sequence<Pair<Int, Manga>>)
            = manga.filter {
        validateDbManga(it.second)
    }.mapNotNull {
                FavoriteEntry().apply {
                    title = it.second.title
                    gid = ExGalleryMetadata.galleryId(it.second.url)
                    token = ExGalleryMetadata.galleryToken(it.second.url)
                    category = it.first

                    if(this.category > 9)
                        return@mapNotNull null
                }
            }

    private fun validateDbManga(manga: Manga)
            = manga.favorite && (manga.source == EH_SOURCE_ID || manga.source == EXH_SOURCE_ID)
}

data class ChangeSet(val added: List<FavoriteEntry>,
                     val removed: List<FavoriteEntry>)
