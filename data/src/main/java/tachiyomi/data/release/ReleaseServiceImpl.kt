package tachiyomi.data.release

import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? {
        val release = with(json) {
            networkService.client
                .newCall(GET("https://api.github.com/repos/${arguments.repository}/releases/latest"))
                .awaitSuccess()
                .parseAs<GithubRelease>()
        }

        val downloadLink = release.getDownloadLink(isFoss = arguments.isFoss) ?: return null

        return Release(
            version = release.version,
            info = release.info.replace(gitHubUsernameMentionRegex) { mention ->
                "[${mention.value}](https://github.com/${mention.value.substring(1)})"
            },
            releaseLink = release.releaseLink,
            downloadLink = downloadLink,
        )
    }

    private fun GithubRelease.getDownloadLink(isFoss: Boolean): String? {
        val map = assets.associate { asset ->
            TYPES.find { "-$it" in asset.name } to asset.downloadLink
        }

        return if (!isFoss) {
            map[Build.SUPPORTED_ABIS[0]] ?: map[null]
        } else {
            map[FOSS]
        }
    }

    companion object {
        private const val ARM64 = "arm64-v8a"
        private const val ARMEABI = "armeabi-v7a"
        private const val X86 = "x86"
        private const val X86_64 = "x86_64"

        private const val FOSS = "foss"

        private val TYPES = listOf(FOSS, ARM64, ARMEABI, X86_64, X86)
    }
}
