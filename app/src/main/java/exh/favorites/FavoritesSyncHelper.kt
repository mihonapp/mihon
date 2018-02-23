package exh.favorites

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.launchUI
import eu.kanade.tachiyomi.util.powerManager
import eu.kanade.tachiyomi.util.toast
import eu.kanade.tachiyomi.util.wifiManager
import exh.EH_METADATA_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.util.ignore
import exh.util.trans
import okhttp3.FormBody
import okhttp3.Request
import rx.subjects.BehaviorSubject
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

class FavoritesSyncHelper(val context: Context) {
    private val db: DatabaseHelper by injectLazy()

    private val prefs: PreferencesHelper by injectLazy()

    private val exh by lazy {
        Injekt.get<SourceManager>().get(EXH_SOURCE_ID) as? EHentai
                ?: EHentai(0, true, context)
    }

    private val storage = LocalFavoritesStorage()

    private val galleryAdder = GalleryAdder()

    private var lastThrottleTime: Long = 0
    private var throttleTime: Long = 0

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    val status = BehaviorSubject.create<FavoritesSyncStatus>(FavoritesSyncStatus.Idle())

    @Synchronized
    fun runSync() {
        if(status.value !is FavoritesSyncStatus.Idle) {
            return
        }

        status.onNext(FavoritesSyncStatus.Initializing())

        thread { beginSync() }
    }

    private fun beginSync() {
        //Check if logged in
        if(!prefs.enableExhentai().getOrDefault()) {
            status.onNext(FavoritesSyncStatus.Error("Please log in!"))
            return
        }

        //Download remote favorites
        val favorites = try {
            status.onNext(FavoritesSyncStatus.Processing("Downloading favorites from remote server"))
            exh.fetchFavorites()
        } catch(e: Exception) {
            status.onNext(FavoritesSyncStatus.Error("Failed to fetch favorites from remote server!"))
            Timber.e(e, "Could not fetch favorites!")
            return
        }

        val errorList = mutableListOf<String>()

        try {
            //Take wake + wifi locks
            ignore { wakeLock?.release() }
            wakeLock = ignore {
                context.powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "ExhFavoritesSyncWakelock")
            }
            ignore { wifiLock?.release() }
            wifiLock = ignore {
                context.wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,
                        "ExhFavoritesSyncWifi")
            }

            storage.getRealm().use { realm ->
                realm.trans {
                    db.inTransaction {
                        status.onNext(FavoritesSyncStatus.Processing("Calculating remote changes"))
                        val remoteChanges = storage.getChangedRemoteEntries(realm, favorites.first)
                        val localChanges = if(prefs.eh_readOnlySync().getOrDefault()) {
                            null //Do not build local changes if they are not going to be applied
                        } else {
                            status.onNext(FavoritesSyncStatus.Processing("Calculating local changes"))
                            storage.getChangedDbEntries(realm)
                        }

                        //Apply remote categories
                        status.onNext(FavoritesSyncStatus.Processing("Updating category names"))
                        applyRemoteCategories(errorList, favorites.second)

                        //Apply change sets
                        applyChangeSetToLocal(errorList, remoteChanges)
                        if(localChanges != null)
                            applyChangeSetToRemote(errorList, localChanges)

                        status.onNext(FavoritesSyncStatus.Processing("Cleaning up"))
                        storage.snapshotEntries(realm)
                    }
                }
            }

            val theContext = context
            launchUI {
                theContext.toast("Sync complete!")
            }
        } catch(e: IgnoredException) {
            //Do not display error as this error has already been reported
            Timber.w(e, "Ignoring exception!")
            return
        } catch (e: Exception) {
            status.onNext(FavoritesSyncStatus.Error("Unknown error: ${e.message}"))
            Timber.e(e, "Sync error!")
            return
        } finally {
            //Release wake + wifi locks
            ignore {
                wakeLock?.release()
                wakeLock = null
            }
            ignore {
                wifiLock?.release()
                wifiLock = null
            }
        }

        if(errorList.isEmpty())
            status.onNext(FavoritesSyncStatus.Idle())
        else
            status.onNext(FavoritesSyncStatus.CompleteWithErrors(errorList))
    }

    private fun applyRemoteCategories(errorList: MutableList<String>, categories: List<String>) {
        val localCategories = db.getCategories().executeAsBlocking()

        val newLocalCategories = localCategories.toMutableList()

        var changed = false

        categories.forEachIndexed { index, remote ->
            val local = localCategories.getOrElse(index) {
                changed = true

                Category.create(remote).apply {
                    order = index

                    //Going through categories list from front to back
                    //If category does not exist, list size <= category index
                    //Thus, we can just add it here and not worry about indexing
                    newLocalCategories += this
                }
            }

            if(local.name != remote) {
                changed = true

                local.name = remote
            }
        }

        //Ensure consistent ordering
        newLocalCategories.forEachIndexed { index, category ->
            if(category.order != index) {
                changed = true

                category.order = index
            }
        }

        //Only insert categories if changed
        if(changed)
            db.insertCategories(newLocalCategories).executeAsBlocking()
    }

    private fun addGalleryRemote(errorList: MutableList<String>, gallery: FavoriteEntry) {
        val url = "${exh.baseUrl}/gallerypopups.php?gid=${gallery.gid}&t=${gallery.token}&act=addfav"

        val request = Request.Builder()
                .url(url)
                .post(FormBody.Builder()
                        .add("favcat", gallery.category.toString())
                        .add("favnote", "")
                        .add("apply", "Add to Favorites")
                        .add("update", "1")
                        .build())
                .build()

        if(!explicitlyRetryExhRequest(10, request)) {
            val errorString =  "Unable to add gallery to remote server: '${gallery.title}' (GID: ${gallery.gid})!"

            if(prefs.eh_lenientSync().getOrDefault()) {
                errorList += errorString
            } else {
                status.onNext(FavoritesSyncStatus.Error(errorString))
                throw IgnoredException()
            }
        }
    }

    private fun explicitlyRetryExhRequest(retryCount: Int, request: Request): Boolean {
        var success = false

        for(i in 1 .. retryCount) {
            try {
                val resp = exh.client.newCall(request).execute()

                if (resp.isSuccessful) {
                    success = true
                    break
                }
            } catch (e: Exception) {
                Timber.e(e, "Sync network error!")
            }
        }

        return success
    }

    private fun applyChangeSetToRemote(errorList: MutableList<String>, changeSet: ChangeSet) {
        //Apply removals
        if(changeSet.removed.isNotEmpty()) {
            status.onNext(FavoritesSyncStatus.Processing("Removing ${changeSet.removed.size} galleries from remote server"))

            val formBody = FormBody.Builder()
                    .add("ddact", "delete")
                    .add("apply", "Apply")

            //Add change set to form
            changeSet.removed.forEach {
                formBody.add("modifygids[]", it.gid)
            }

            val request = Request.Builder()
                    .url("https://exhentai.org/favorites.php")
                    .post(formBody.build())
                    .build()

            if(!explicitlyRetryExhRequest(10, request)) {
                val errorString = "Unable to delete galleries from the remote servers!"

                if(prefs.eh_lenientSync().getOrDefault()) {
                    errorList += errorString
                } else {
                    status.onNext(FavoritesSyncStatus.Error(errorString))
                    throw IgnoredException()
                }
            }
        }

        //Apply additions
        resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.onNext(FavoritesSyncStatus.Processing("Adding gallery ${index + 1} of ${changeSet.added.size} to remote server",
                    needWarnThrottle()))

            throttle()

            addGalleryRemote(errorList, it)
        }
    }

    private fun applyChangeSetToLocal(errorList: MutableList<String>, changeSet: ChangeSet) {
        val removedManga = mutableListOf<Manga>()

        //Apply removals
        changeSet.removed.forEachIndexed { index, it ->
            status.onNext(FavoritesSyncStatus.Processing("Removing gallery ${index + 1} of ${changeSet.removed.size} from local library"))
            val url = it.getUrl()

            //Consider both EX and EH sources
            listOf(db.getManga(url, EXH_SOURCE_ID),
                    db.getManga(url, EH_METADATA_SOURCE_ID)).forEach {
                val manga = it.executeAsBlocking()

                if(manga?.favorite == true) {
                    manga.favorite = false
                    db.updateMangaFavorite(manga).executeAsBlocking()
                    removedManga += manga
                }
            }
        }

        // Can't do too many DB OPs in one go
        removedManga.chunked(10).forEach {
            db.deleteOldMangasCategories(it).executeAsBlocking()
        }

        val insertedMangaCategories = mutableListOf<Pair<MangaCategory, Manga>>()
        val categories = db.getCategories().executeAsBlocking()

        //Apply additions
        resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.onNext(FavoritesSyncStatus.Processing("Adding gallery ${index + 1} of ${changeSet.added.size} to local library",
                    needWarnThrottle()))

            throttle()

            //Import using gallery adder
            val result = galleryAdder.addGallery("${exh.baseUrl}${it.getUrl()}",
                    true,
                    EXH_SOURCE_ID)

            if(result is GalleryAddEvent.Fail) {
                val errorString = "Failed to add gallery to local database: " + when (result) {
                    is GalleryAddEvent.Fail.Error -> "'${it.title}' ${result.logMessage}"
                    is GalleryAddEvent.Fail.UnknownType -> "'${it.title}' (${result.galleryUrl}) is not a valid gallery!"
                }

                if(prefs.eh_lenientSync().getOrDefault()) {
                    errorList += errorString
                } else {
                    status.onNext(FavoritesSyncStatus.Error(errorString))
                    throw IgnoredException()
                }
            } else if(result is GalleryAddEvent.Success) {
                insertedMangaCategories += MangaCategory.create(result.manga,
                        categories[it.category]) to result.manga
            }
        }

        // Can't do too many DB OPs in one go
        insertedMangaCategories.chunked(10).map {
            Pair(it.map { it.first }, it.map { it.second })
        }.forEach {
                    db.setMangaCategories(it.first, it.second)
                }
    }

    fun throttle() {
        //Throttle requests if necessary
        val now = System.currentTimeMillis()
        val timeDiff = now - lastThrottleTime
        if(timeDiff < throttleTime)
            Thread.sleep(throttleTime - timeDiff)

        if(throttleTime < THROTTLE_MAX)
            throttleTime += THROTTLE_INC

        lastThrottleTime = System.currentTimeMillis()
    }

    fun resetThrottle() {
        lastThrottleTime = 0
        throttleTime = 0
    }

    fun needWarnThrottle()
            = throttleTime >= THROTTLE_WARN

    class IgnoredException : RuntimeException()

    companion object {
        private const val THROTTLE_MAX = 5000
        private const val THROTTLE_INC = 10
        private const val THROTTLE_WARN = 1000
    }
}

sealed class FavoritesSyncStatus(val message: String) {
    class Error(message: String) : FavoritesSyncStatus(message)
    class Idle : FavoritesSyncStatus("Waiting for sync to start")
    class Initializing : FavoritesSyncStatus("Initializing sync")
    class Processing(message: String, isThrottle: Boolean = false) : FavoritesSyncStatus(if(isThrottle)
        (message + "\n\nSync is currently throttling (to avoid being banned from ExHentai) and may take a long time to complete.")
    else
        message)
    class CompleteWithErrors(messages: List<String>) : FavoritesSyncStatus(messages.joinToString("\n"))
}
