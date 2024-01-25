package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class BangumiInterceptor(private val bangumi: Bangumi) : Interceptor {

    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    private var oauth: OAuth? = bangumi.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val currAuth = oauth ?: throw Exception("Not authenticated with Bangumi")

        if (currAuth.isExpired()) {
            val response = chain.proceed(BangumiApi.refreshTokenRequest(currAuth.refresh_token!!))
            if (response.isSuccessful) {
                newAuth(json.decodeFromString<OAuth>(response.body.string()))
            } else {
                response.close()
            }
        }

        return originalRequest.newBuilder()
            .header(
                "User-Agent",
                "antsylich/Mihon/v${BuildConfig.VERSION_NAME} (Android) (http://github.com/mihonapp/mihon)",
            )
            .apply {
                if (originalRequest.method == "GET") {
                    val newUrl = originalRequest.url.newBuilder()
                        .addQueryParameter("access_token", currAuth.access_token)
                        .build()
                    url(newUrl)
                } else {
                    post(addToken(currAuth.access_token, originalRequest.body as FormBody))
                }
            }
            .build()
            .let(chain::proceed)
    }

    fun newAuth(oauth: OAuth?) {
        this.oauth = if (oauth == null) {
            null
        } else {
            OAuth(
                oauth.access_token,
                oauth.token_type,
                System.currentTimeMillis() / 1000,
                oauth.expires_in,
                oauth.refresh_token,
                this.oauth?.user_id,
            )
        }

        bangumi.saveToken(oauth)
    }

    private fun addToken(token: String, oidFormBody: FormBody): FormBody {
        val newFormBody = FormBody.Builder()
        for (i in 0..<oidFormBody.size) {
            newFormBody.add(oidFormBody.name(i), oidFormBody.value(i))
        }
        newFormBody.add("access_token", token)
        return newFormBody.build()
    }
}
