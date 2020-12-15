package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

class AnilistLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(data: Uri?) {
        val regex = "(?:access_token=)(.*?)(?:&)".toRegex()
        val matchResult = regex.find(data?.fragment.toString())
        if (matchResult?.groups?.get(1) != null) {
            trackManager.aniList.login(matchResult.groups[1]!!.value)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        returnToSettings()
                    },
                    {
                        returnToSettings()
                    }
                )
        } else {
            trackManager.aniList.logout()
            returnToSettings()
        }
    }
}
