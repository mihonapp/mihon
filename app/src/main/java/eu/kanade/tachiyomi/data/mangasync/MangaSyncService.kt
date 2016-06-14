package eu.kanade.tachiyomi.data.mangasync

import android.content.Context
import android.support.annotation.CallSuper
import eu.kanade.tachiyomi.data.database.models.MangaSync
import eu.kanade.tachiyomi.data.network.NetworkHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import okhttp3.OkHttpClient
import rx.Completable
import rx.Observable
import uy.kohesive.injekt.injectLazy

abstract class MangaSyncService(private val context: Context, val id: Int) {

    val preferences: PreferencesHelper by injectLazy()
    val networkService: NetworkHelper by injectLazy()

    open val client: OkHttpClient
        get() = networkService.client

    // Name of the manga sync service to display
    abstract val name: String

    abstract fun login(username: String, password: String): Completable

    open val isLogged: Boolean
        get() = !getUsername().isEmpty() &&
                !getPassword().isEmpty()

    abstract fun add(manga: MangaSync): Observable<MangaSync>

    abstract fun update(manga: MangaSync): Observable<MangaSync>

    abstract fun bind(manga: MangaSync): Observable<MangaSync>

    abstract fun getStatus(status: Int): String

    fun saveCredentials(username: String, password: String) {
        preferences.setMangaSyncCredentials(this, username, password)
    }

    @CallSuper
    open fun logout() {
        preferences.setMangaSyncCredentials(this, "", "")
    }

    fun getUsername() = preferences.mangaSyncUsername(this)

    fun getPassword() = preferences.mangaSyncPassword(this)

}
