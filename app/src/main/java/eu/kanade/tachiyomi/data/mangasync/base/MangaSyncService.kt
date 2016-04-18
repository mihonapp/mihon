package eu.kanade.tachiyomi.data.mangasync.base

import android.content.Context
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.data.database.models.MangaSync
import eu.kanade.tachiyomi.data.network.NetworkHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import okhttp3.Response
import rx.Observable
import javax.inject.Inject

abstract class MangaSyncService(private val context: Context, val id: Int) {

    @Inject lateinit var preferences: PreferencesHelper
    @Inject lateinit var networkService: NetworkHelper

    init {
        App.get(context).component.inject(this)
    }

    // Name of the manga sync service to display
    abstract val name: String

    abstract fun login(username: String, password: String): Observable<Boolean>

    open val isLogged: Boolean
        get() = !preferences.mangaSyncUsername(this).isEmpty() &&
                !preferences.mangaSyncPassword(this).isEmpty()

    abstract fun update(manga: MangaSync): Observable<Response>

    abstract fun add(manga: MangaSync): Observable<Response>

    abstract fun bind(manga: MangaSync): Observable<Response>

    abstract fun getStatus(status: Int): String

}
