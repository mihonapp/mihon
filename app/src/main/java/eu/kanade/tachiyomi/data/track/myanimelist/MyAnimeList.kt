package eu.kanade.tachiyomi.data.track.myanimelist

import android.content.Context
import android.graphics.Color
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rx.Completable
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

class MyAnimeList(private val context: Context, id: Int) : TrackService(id) {

    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 6
        const val REREADING = 7
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { MyAnimeListInterceptor(this, getPassword()) }
    private val api by lazy { MyAnimeListApi(client, interceptor) }

    override val name: String
        get() = "MyAnimeList"

    override fun getLogo() = R.drawable.ic_tracker_mal

    override fun getLogoColor() = Color.rgb(46, 81, 162)

    override fun getStatusList(): List<Int> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)
    }

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(R.string.reading)
            COMPLETED -> getString(R.string.completed)
            ON_HOLD -> getString(R.string.on_hold)
            DROPPED -> getString(R.string.dropped)
            PLAN_TO_READ -> getString(R.string.plan_to_read)
            REREADING -> getString(R.string.repeating)
            else -> ""
        }
    }

    override fun getCompletionStatus(): Int = COMPLETED

    override fun getScoreList(): List<String> {
        return IntRange(0, 10).map(Int::toString)
    }

    override fun displayScore(track: Track): String {
        return track.score.toInt().toString()
    }

    override fun add(track: Track): Observable<Track> {
        return runAsObservable { api.addItemToList(track) }
    }

    override fun update(track: Track): Observable<Track> {
        return runAsObservable { api.updateItem(track) }
    }

    override fun bind(track: Track): Observable<Track> {
        // TODO: change this to call add and update like the other trackers?
        return runAsObservable { api.getListItem(track) }
    }

    override fun search(query: String): Observable<List<TrackSearch>> {
        return runAsObservable { api.search(query) }
    }

    override fun refresh(track: Track): Observable<Track> {
        return runAsObservable { api.getListItem(track) }
    }

    override fun login(username: String, password: String) = login(password)

    fun login(authCode: String): Completable {
        return Completable.fromCallable {
            val oauth = runBlocking { api.getAccessToken(authCode) }
            interceptor.setAuth(oauth)
            val username = runBlocking { api.getCurrentUser() }
            saveCredentials(username, oauth.access_token)
        }
            .doOnError {
                Timber.e(it)
                logout()
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun logout() {
        super.logout()
        preferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    fun saveOAuth(oAuth: OAuth?) {
        preferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    fun loadOAuth(): OAuth? {
        return try {
            json.decodeFromString<OAuth>(preferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }

    private fun <T> runAsObservable(block: suspend () -> T): Observable<T> {
        return Observable.fromCallable { runBlocking(Dispatchers.IO) { block() } }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
    }
}
