package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.toast
import rx.Observable


class GithubUpdateChecker(private val context: Context) {

    val service: GithubService = GithubService.create()

    /**
     * Returns observable containing release information
     */
    fun checkForApplicationUpdate(): Observable<GithubRelease> {
        context.toast(R.string.update_check_look_for_updates)
        return service.getLatestVersion()
    }
}