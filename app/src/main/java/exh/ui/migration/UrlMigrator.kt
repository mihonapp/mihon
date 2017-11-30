package exh.ui.migration

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import exh.isExSource
import exh.isLewdSource
import exh.metadata.models.ExGalleryMetadata
import exh.util.realmTrans
import uy.kohesive.injekt.injectLazy

class UrlMigrator {
    private val db: DatabaseHelper by injectLazy()

    private val prefs: PreferencesHelper by injectLazy()

    fun perform() {
        db.inTransaction {
            val dbMangas = db.getMangas()
                    .executeAsBlocking()

            //Find all EX mangas
            val qualifyingMangas = dbMangas.asSequence().filter {
                isLewdSource(it.source)
            }

            val possibleDups = mutableListOf<Manga>()
            val badMangas = mutableListOf<Manga>()

            qualifyingMangas.forEach {
                if(it.url.startsWith("g/")) //Missing slash at front so we are bad
                    badMangas.add(it)
                else
                    possibleDups.add(it)
            }

            //Sort possible dups so we can use binary search on it
            possibleDups.sortBy { it.url }

            realmTrans { realm ->
                badMangas.forEach { manga ->
                    //Build fixed URL
                    val urlWithSlash = "/" + manga.url
                    //Fix metadata if required
                    val metadata = ExGalleryMetadata.UrlQuery(manga.url, isExSource(manga.source))
                            .query(realm)
                            .findFirst()
                    metadata?.url?.let {
                        if (it.startsWith("g/")) { //Check if metadata URL has no slash
                            metadata.url = urlWithSlash //Fix it
                        }
                    }
                    //If we have a dup (with the fixed url), use the dup instead
                    val possibleDup = possibleDups.binarySearchBy(urlWithSlash, selector = { it.url })
                    if (possibleDup >= 0) {
                        //Make sure it is favorited if we are
                        if (manga.favorite) {
                            val dup = possibleDups[possibleDup]
                            dup.favorite = true
                            db.insertManga(dup).executeAsBlocking() //Update DB with changes
                        }
                        //Delete ourself (but the dup is still there)
                        db.deleteManga(manga).executeAsBlocking()
                        return@forEach
                    }
                    //No dup, correct URL and reinsert ourselves
                    manga.url = urlWithSlash
                    db.insertManga(manga).executeAsBlocking()
                }
            }
        }
    }

    fun tryMigration() {
        if(!prefs.hasPerformedURLMigration().getOrDefault()) {
            perform()
            prefs.hasPerformedURLMigration().set(true)
        }
    }
}
