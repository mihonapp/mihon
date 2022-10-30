package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.isInstalledFromFDroid
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.concurrent.TimeUnit

class AppUpdateChecker {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val lastAppCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_app_check", 0)
    }

    suspend fun checkForUpdate(context: Context, isUserPrompt: Boolean = false): AppUpdateResult {
        // Limit checks to once a day at most
        if (isUserPrompt.not() && Date().time < lastAppCheck.get() + TimeUnit.DAYS.toMillis(1)) {
            return AppUpdateResult.NoNewUpdate
        }

        return withIOContext {
            val result = networkService.client
                .newCall(GET("https://api.github.com/repos/$GITHUB_REPO/releases/latest"))
                .await()
                .parseAs<GithubRelease>()
                .let {
                    lastAppCheck.set(Date().time)

                    // Check if latest version is different from current version
                    if (isNewVersion(it.version)) {
                        if (context.isInstalledFromFDroid()) {
                            AppUpdateResult.NewUpdateFdroidInstallation
                        } else {
                            AppUpdateResult.NewUpdate(it)
                        }
                    } else {
                        AppUpdateResult.NoNewUpdate
                    }
                }

            when (result) {
                is AppUpdateResult.NewUpdate -> AppUpdateNotifier(context).promptUpdate(result.release)
                is AppUpdateResult.NewUpdateFdroidInstallation -> AppUpdateNotifier(context).promptFdroidUpdate()
                else -> {}
            }

            result
        }
    }

    private fun isNewVersion(versionTag: String): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")

        return if (BuildConfig.PREVIEW) {
            // Preview builds: based on releases in "tachiyomiorg/tachiyomi-preview" repo
            // tagged as something like "r1234"
            newVersion.toInt() > BuildConfig.COMMIT_COUNT.toInt()
        } else {
            // Release builds: based on releases in "tachiyomiorg/tachiyomi" repo
            // tagged as something like "v0.1.2"
            val oldVersion = BuildConfig.VERSION_NAME.replace("[^\\d.]".toRegex(), "")

            val newSemVer = newVersion.split(".").map { it.toInt() }
            val oldSemVer = oldVersion.split(".").map { it.toInt() }

            oldSemVer.mapIndexed { index, i ->
                if (newSemVer[index] > i) {
                    return true
                }
            }

            false
        }
    }
}

val GITHUB_REPO: String by lazy {
    if (BuildConfig.PREVIEW) {
        "tachiyomiorg/tachiyomi-preview"
    } else {
        "tachiyomiorg/tachiyomi"
    }
}

val RELEASE_TAG: String by lazy {
    if (BuildConfig.PREVIEW) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
}

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
