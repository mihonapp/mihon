package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isInstalledFromFDroid
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): GetApplicationRelease.Result {
        // Disable app update checks for older Android versions that we're going to drop support for
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        //     return GetApplicationRelease.Result.OsTooOld
        // }

        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isPreview = BuildConfig.PREVIEW,
                    isThirdParty = context.isInstalledFromFDroid(),
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    versionName = BuildConfig.VERSION_NAME,
                    repository = GITHUB_REPO.get(),
                    changeRepository = { GITHUB_REPO.set(it) },
                    kujakuKey = KUJAKU_KEY,
                    forceCheck = forceCheck,
                ),
            )

            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> AppUpdateNotifier(context).promptUpdate(result.release)
                is GetApplicationRelease.Result.ThirdPartyInstallation -> AppUpdateNotifier(
                    context,
                ).promptFdroidUpdate()
                else -> {}
            }

            result
        }
    }
}

private val KUJAKU_KEY by lazy {
    if (BuildConfig.PREVIEW) {
        "mihon-preview-0.x"
    } else {
        "mihon-0.x"
    }
}

val GITHUB_REPO: Preference<String> by lazy {
    Injekt.get<BasePreferences>().releaseGithubRepo()
}

val RELEASE_TAG: String by lazy {
    if (BuildConfig.PREVIEW) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
}

val RELEASE_URL = "https://github.com/${GITHUB_REPO.get()}/releases/tag/$RELEASE_TAG"
