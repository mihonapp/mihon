package eu.kanade.tachiyomi.extension.api

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.serialization.Serializable
import uy.kohesive.injekt.injectLazy
import java.util.Date

internal class ExtensionGithubApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            networkService.client
                .newCall(GET("${REPO_URL_PREFIX}index.min.json"))
                .await()
                .parseAs<List<ExtensionJsonObject>>()
                .toExtensions()
        }
    }

    suspend fun checkForUpdates(context: Context): List<Extension.Installed> {
        val extensions = findExtensions()

        preferences.lastExtCheck().set(Date().time)

        val installedExtensions = ExtensionLoader.loadExtensions(context)
            .filterIsInstance<LoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<Extension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue

            val hasUpdate = availableExt.versionCode > installedExt.versionCode
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(): List<Extension.Available> {
        return this
            .filter {
                val libVersion = it.version.substringBeforeLast('.').toDouble()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                Extension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    apkName = it.apk,
                    iconUrl = "${REPO_URL_PREFIX}icon/${it.apk.replace(".apk", ".png")}"
                )
            }
    }

    fun getApkUrl(extension: Extension.Available): String {
        return "${REPO_URL_PREFIX}apk/${extension.apkName}"
    }
}

private const val REPO_URL_PREFIX = "https://raw.githubusercontent.com/tachiyomiorg/tachiyomi-extensions/repo/"

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val version: String,
    val code: Long,
    val lang: String,
    val nsfw: Int,
)
