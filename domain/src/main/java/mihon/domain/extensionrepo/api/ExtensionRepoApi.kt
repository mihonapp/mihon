package mihon.domain.extensionrepo.api

import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.domain.extensionrepo.model.ExtensionRepo
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

class ExtensionRepoApi(
    private val client: OkHttpClient,
) {

    private val json: Json by injectLazy()

    suspend fun fetchRepoDetails(
        repo: String,
    ): ExtensionRepo? {
        return withIOContext {
            val url = "$repo/repo.json".toUri()

            try {
                with(json) {
                    client.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<JsonObject>()
                        .let {
                            it["meta"]
                                ?.jsonObject
                                ?.let { it1 -> jsonToExtensionRepo(baseUrl = repo, it1) }
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
