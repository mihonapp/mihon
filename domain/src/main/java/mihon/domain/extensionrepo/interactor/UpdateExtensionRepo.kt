package mihon.domain.extensionrepo.interactor

import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.core.common.util.lang.withIOContext

class UpdateExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository,
    private val json: Json,
    private val networkService: NetworkHelper,
) {

    suspend fun await(repo: ExtensionRepo): ExtensionRepo? {
        val newRepo = fetchRepoDetails(repo.baseUrl)
        newRepo?.let {
            if (repo.fingerprint.startsWith("NOFINGERPRINT") || repo.fingerprint == newRepo.fingerprint) {
                extensionRepoRepository.upsertRepository(newRepo)
            }
            return newRepo
        }
        return null
    }

    private suspend fun fetchRepoDetails(baseUrl: String): ExtensionRepo? {
        return withIOContext {
            val url = "$baseUrl/repo.json".toUri()

            try {
                with(json) {
                    networkService.client.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<JsonObject>()
                        .let {
                            it["meta"]
                                ?.jsonObject
                                ?.let { it1 -> jsonToExtensionRepo(baseUrl = baseUrl, it1) }
                        }
                }
            } catch (_: HttpException) {
                null
            }
        }
    }

    private fun jsonToExtensionRepo(baseUrl: String, obj: JsonObject): ExtensionRepo? {
        return try {
            ExtensionRepo(
                baseUrl = baseUrl,
                name = obj["name"]!!.jsonPrimitive.content,
                shortName = obj["shortName"]?.jsonPrimitive?.content,
                website = obj["website"]!!.jsonPrimitive.content,
                fingerprint = obj["signingKeyFingerprint"]!!.jsonPrimitive.content,
            )
        } catch (_: NullPointerException) {
            null
        }
    }

}
