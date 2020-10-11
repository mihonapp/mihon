package eu.kanade.tachiyomi.extension.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import uy.kohesive.injekt.injectLazy

/**
 * Used to get the extension repo listing from GitHub.
 */
interface ExtensionGithubService {

    companion object {
        private val client by lazy {
            val network: NetworkHelper by injectLazy()
            network.client.newBuilder()
                .addNetworkInterceptor { chain ->
                    val originalResponse = chain.proceed(chain.request())
                    originalResponse.newBuilder()
                        .header("Content-Encoding", "gzip")
                        .header("Content-Type", "application/json")
                        .build()
                }
                .build()
        }

        fun create(): ExtensionGithubService {
            val adapter = Retrofit.Builder()
                .baseUrl(ExtensionGithubApi.BASE_URL)
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .client(client)
                .build()

            return adapter.create(ExtensionGithubService::class.java)
        }
    }

    @GET("${ExtensionGithubApi.REPO_URL_PREFIX}index.json.gz")
    suspend fun getRepo(): JsonArray
}
