package exh

import android.app.Activity
import android.support.v7.app.AlertDialog
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.syncChaptersWithSource
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

class FavoritesSyncHelper(val activity: Activity) {

    val db: DatabaseHelper by injectLazy()

    val sourceManager: SourceManager by injectLazy()

    val prefs: PreferencesHelper by injectLazy()

    fun guiSyncFavorites(onComplete: () -> Unit) {
        //ExHentai must be enabled/user must be logged in
        if (!prefs.enableExhentai().getOrDefault()) {
            AlertDialog.Builder(activity).setTitle("Error")
                    .setMessage("You are not logged in! Please log in and try again!")
                    .setPositiveButton("Ok") { dialog, _ -> dialog.dismiss() }.show()
            return
        }
        val dialog = MaterialDialog.Builder(activity)
                .progress(true, 0)
                .title("Downloading favorites")
                .content("Please wait...")
                .cancelable(false)
                .show()
        thread {
            var error = false
            try {
                syncFavorites()
            } catch (e: Exception) {
                error = true
                Timber.e(e, "Could not sync favorites!")
            }

            dialog.dismiss()

            activity.runOnUiThread {
                if (error)
                    MaterialDialog.Builder(activity)
                            .title("Error")
                            .content("There was an error downloading your favorites, please try again later!")
                            .positiveText("Ok")
                            .show()
                onComplete()
            }
        }
    }

    fun syncFavorites() {
        val onlineSources = sourceManager.getOnlineSources()
        var ehSource: EHentai? = null
        var exSource: EHentai? = null
        onlineSources.forEach {
            if(it.id == EH_SOURCE_ID)
                ehSource = it as EHentai
            else if(it.id == EXH_SOURCE_ID)
                exSource = it as EHentai
        }

        (exSource ?: ehSource)?.let { source ->
            val favResponse = source.fetchFavorites()
            val ourCategories = ArrayList<Category>(db.getCategories().executeAsBlocking())
            val ourMangas = ArrayList<Manga>(db.getMangas().executeAsBlocking())
            //Add required categories (categories do not sync upwards)
            favResponse.second.filter { theirCategory ->
                ourCategories.find {
                    it.name.endsWith(theirCategory)
                } == null
            }.map {
                Category.create(it)
            }.let {
                db.inTransaction {
                    //Insert new categories
                    db.insertCategories(it).executeAsBlocking().results().entries.filter {
                        it.value.wasInserted()
                    }.forEach { it.key.id = it.value.insertedId()!!.toInt() }

                    val categoryMap = (it + ourCategories).associateBy { it.name }

                    //Insert new mangas
                    val mangaToInsert = java.util.ArrayList<Manga>()
                    favResponse.first.map {
                        val category = categoryMap[it.fav]!!
                        var manga = it.manga
                        val alreadyHaveManga = ourMangas.find {
                            it.url == manga.url
                        }?.apply {
                            manga = this
                        } != null
                        if (!alreadyHaveManga) {
                            ourMangas.add(manga)
                            mangaToInsert.add(manga)
                        }
                        manga.favorite = true
                        Pair(manga, category)
                    }.apply {
                        //Insert mangas
                        db.insertMangas(mangaToInsert).executeAsBlocking().results().entries.filter {
                            it.value.wasInserted()
                        }.forEach { manga ->
                            manga.key.id = manga.value.insertedId()
                            try {
                                source.fetchChapterList(manga.key).map {
                                    syncChaptersWithSource(db, it, manga.key, source)
                                }.toBlocking().first()
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to update chapters for gallery: ${manga.key.title}!")
                            }
                        }

                        //Set categories
                        val categories = map { MangaCategory.create(it.first, it.second) }
                        val mangas = map { it.first }
                        db.setMangaCategories(categories, mangas)
                    }
                }
            }
        }
    }
}