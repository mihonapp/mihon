package exh.eh

import android.content.Context
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import rx.Observable
import rx.Single
import uy.kohesive.injekt.injectLazy
import java.io.File

data class ChapterChain(val manga: Manga, val chapters: List<Chapter>)

class EHentaiUpdateHelper(context: Context) {
    val parentLookupTable =
            MemAutoFlushingLookupTable(
                    File(context.filesDir, "exh-plt.maftable"),
                    GalleryEntry.Serializer()
            )
    private val db: DatabaseHelper by injectLazy()

    /**
     * @param chapters Cannot be an empty list!
     *
     * @return Triple<Accepted, Discarded, HasNew>
     */
    fun findAcceptedRootAndDiscardOthers(sourceId: Long, chapters: List<Chapter>): Single<Triple<ChapterChain, List<ChapterChain>, Boolean>> {
        // Find other chains
        val chainsObservable = Observable.merge(chapters.map { chapter ->
            db.getChapters(chapter.url).asRxSingle().toObservable()
        }).toList().map { allChapters ->
            allChapters.flatMap { innerChapters -> innerChapters.map { it.manga_id!! } }.distinct()
        }.flatMap { mangaIds ->
            Observable.merge(
                    mangaIds.map { mangaId ->
                        Single.zip(
                                db.getManga(mangaId).asRxSingle(),
                                db.getChaptersByMangaId(mangaId).asRxSingle()
                        ) { manga, chapters ->
                            ChapterChain(manga, chapters)
                        }.toObservable().filter {
                            it.manga.source == sourceId
                        }
                    }
            )
        }.toList()

        // Accept oldest chain
        val chainsWithAccepted = chainsObservable.map { chains ->
            val acceptedChain = chains.minBy { it.manga.id!! }!!

            acceptedChain to chains
        }

        return chainsWithAccepted.map { (accepted, chains) ->
            val toDiscard = chains.filter { it.manga.favorite && it.manga.id != accepted.manga.id }

            val chainsAsChapters = chains.flatMap { it.chapters }

            if(toDiscard.isNotEmpty()) {
                var new = false

                // Copy chain chapters to curChapters
                val newChapters = toDiscard
                        .flatMap { chain ->
                            val meta by lazy {
                                db.getFlatMetadataForManga(chain.manga.id!!)
                                        .executeAsBlocking()
                                        ?.raise<EHentaiSearchMetadata>()
                            }

                            chain.chapters.map { chapter ->
                                // Convert old style chapters to new style chapters if possible
                                if(chapter.date_upload <= 0
                                        && meta?.datePosted != null
                                        && meta?.title != null) {
                                    chapter.name = meta!!.title!!
                                    chapter.date_upload = meta!!.datePosted!!
                                }
                                chapter
                            }
                        }
                        .fold(accepted.chapters) { curChapters, chapter ->
                            val existing = curChapters.find { it.url == chapter.url }

                            val newLastPageRead = chainsAsChapters.maxBy { it.last_page_read }?.last_page_read

                            if (existing != null) {
                                existing.read = existing.read || chapter.read
                                existing.last_page_read = existing.last_page_read.coerceAtLeast(chapter.last_page_read)
                                if(newLastPageRead != null && existing.last_page_read <= 0) {
                                    existing.last_page_read = newLastPageRead
                                }
                                existing.bookmark = existing.bookmark || chapter.bookmark
                                curChapters
                            } else if (chapter.date_upload > 0) { // Ignore chapters using the old system
                                new = true
                                curChapters + ChapterImpl().apply {
                                    manga_id = accepted.manga.id
                                    url = chapter.url
                                    name = chapter.name
                                    read = chapter.read
                                    bookmark = chapter.bookmark

                                    last_page_read = chapter.last_page_read
                                    if(newLastPageRead != null && last_page_read <= 0) {
                                        last_page_read = newLastPageRead
                                    }

                                    date_fetch = chapter.date_fetch
                                    date_upload = chapter.date_upload
                                }
                            } else curChapters
                        }
                        .filter { it.date_upload > 0 } // Ignore chapters using the old system (filter after to prevent dupes from insert)
                        .sortedBy { it.date_upload }
                        .apply {
                            mapIndexed { index, chapter ->
                                chapter.name = "v${index + 1}: " + chapter.name.substringAfter(" ")
                                chapter.chapter_number = index + 1f
                                chapter.source_order = lastIndex - index
                            }
                        }

                toDiscard.forEach { it.manga.favorite = false }
                accepted.manga.favorite = true

                val newAccepted = ChapterChain(accepted.manga, newChapters)
                val rootsToMutate = toDiscard + newAccepted

                db.inTransaction {
                    // Apply changes to all manga
                    db.insertMangas(rootsToMutate.map { it.manga }).executeAsBlocking()
                    // Insert new chapters for accepted manga
                    db.insertChapters(newAccepted.chapters).executeAsBlocking()
                    // Copy categories from all chains to accepted manga
                    val newCategories = rootsToMutate.flatMap {
                        db.getCategoriesForManga(it.manga).executeAsBlocking()
                    }.distinctBy { it.id }.map {
                        MangaCategory.create(newAccepted.manga, it)
                    }
                    db.setMangaCategories(newCategories, rootsToMutate.map { it.manga })
                }

                Triple(newAccepted, toDiscard, new)
            } else Triple(accepted, emptyList(), false)
        }.toSingle()
    }
}

data class GalleryEntry(val gId: String, val gToken: String) {
    class Serializer: MemAutoFlushingLookupTable.EntrySerializer<GalleryEntry> {
        /**
         * Serialize an entry as a String.
         */
        override fun write(entry: GalleryEntry) = with(entry) { "$gId:$gToken" }

        /**
         * Read an entry from a String.
         */
        override fun read(string: String): GalleryEntry {
            val colonIndex = string.indexOf(':')
            return GalleryEntry(
                    string.substring(0, colonIndex),
                    string.substring(colonIndex + 1, string.length)
            )
        }
    }
}
