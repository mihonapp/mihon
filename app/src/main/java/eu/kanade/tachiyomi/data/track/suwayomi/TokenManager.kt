package eu.kanade.tachiyomi.data.track.suwayomi

import android.util.Log
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.network.POST
import kotlinx.coroutines.flow.single
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TokenManager(
    private val mode: SuwayomiApi.AuthMode,
    private val user: String,
    private val pass: String,
    private val baseUrl: String,
    private val baseClient: OkHttpClient,
) {
    private var currentToken: String? = null
    private var refreshToken: String? = null
    private var cookies: String = ""
    private val json = Injekt.get<Json>()


    private data class TokenTuple(val accessToken: String?, val refreshToken: String?)

    public data class HttpHeader(
        val name: String,
        val value: String,
    )

    public fun getBasicHeaders(): List<HttpHeader> {
        val headers = mutableListOf<HttpHeader>()
        if (mode == SuwayomiApi.AuthMode.BASIC_AUTH && pass.isNotEmpty() && user.isNotEmpty()) {
            val credentials = Credentials.basic(user, pass)
            headers.add(HttpHeader("Authorization", credentials))
        }
        return headers.toList()
    }

    public fun getHeaders(): List<HttpHeader> {
        val headers = mutableListOf<HttpHeader>()
        when (mode) {
            SuwayomiApi.AuthMode.NONE -> { }
            SuwayomiApi.AuthMode.BASIC_AUTH -> {
                val credentials = Credentials.basic(user, pass)
                headers.add(HttpHeader("Authorization", credentials))
            }
            SuwayomiApi.AuthMode.SIMPLE_LOGIN -> headers.add(HttpHeader("Cookie", cookies))
            SuwayomiApi.AuthMode.UI_LOGIN -> headers.add(HttpHeader("Authorization", "Bearer $currentToken"))
        }
        return headers.toList()
    }

    public fun token(): Any? {
        return when (mode) {
            SuwayomiApi.AuthMode.SIMPLE_LOGIN -> cookies
            SuwayomiApi.AuthMode.UI_LOGIN -> TokenTuple(currentToken, refreshToken)
            else -> null
        }
    }

    public fun Request.Builder.addToken(): Request.Builder {
        return when (mode) {
            SuwayomiApi.AuthMode.SIMPLE_LOGIN -> this.header("Cookie", cookies)
            SuwayomiApi.AuthMode.UI_LOGIN -> this.header("Authorization", "Bearer $currentToken")
            else -> this
        }
    }

    public suspend fun refresh(oldToken: Any?) {
        Log.v(TAG, "Refreshing token for mode $mode")
        when (mode) {
            SuwayomiApi.AuthMode.SIMPLE_LOGIN -> {
                if (oldToken != cookies) {
                    Log.i(TAG, "Refusing to refresh cookie: Changed since original call, another request likely already refreshed, try again")
                    return
                }

                val formBody = FormBody.Builder().add("user", user).add("pass", pass).build()
                val request = POST(baseUrl + "/login.html", body = formBody)
                val result = baseClient.newBuilder().followRedirects(false).build().newCall(request).await()
                // login.html redirects when successful
                if (!result.isRedirect) {
                    var err = result.body.string().replace(".*<div class=\"error\">([^<]*)</div>.*".toRegex(RegexOption.DOT_MATCHES_ALL)) {
                        it.groups[1]!!.value
                    }
                    Log.v(TAG, "Cookie refresh failed, server did not redirect, error: $err")
                    throw Exception("Login failed: $err")
                }
                cookies = result.header("Set-Cookie", "")!!
                Log.v(TAG, "Cookie successfully refreshed")
            }
            SuwayomiApi.AuthMode.UI_LOGIN -> {
                if (oldToken != TokenTuple(currentToken, refreshToken)) {
                    Log.i(TAG, "Refusing to refresh token: Changed since original call, another request likely already refreshed, try again")
                    return
                }
                val contentType = "application/json".toMediaType()

                with (json) {
                    refreshToken?.let {
                        Log.v(TAG, "Refresh token known, asking for new access token")
                        val requestBody = buildJsonObject {
                            put("query", "mutation REFRESH(\$input: RefreshTokenInput!) {\n  refreshToken(input: \$input) {\n    accessToken\n  }\n}")
                            put("variables", buildJsonObject {
                                put("input", buildJsonObject {
                                    put("refreshToken", it)
                                })
                            })
                            put("operationName", "REFRESH")
                        }.toString().toRequestBody(contentType)
                        val response = baseClient.newCall(
                            POST(
                                "$baseUrl/api/graphql",
                                body = requestBody,
                            ),
                        )
                            .awaitSuccess()
                            .parseAs<GraphQlResult<RefreshTokenPayload1>>()
                        if (response.errors?.let { it.size > 0 } == true) {
                            Log.w(TAG, "Invalid refresh token: ${response.errors[0].message}")
                            refreshToken = null
                        }

                        currentToken = response.data!!.refreshToken.accessToken
                        return
                    }

                    Log.v(TAG, "No previous login, asking for tokens with username and password")
                    val requestBody = buildJsonObject {
                        put("query", "mutation LOGIN(\$input: LoginInput!) {\n  login(input: \$input) {\n    accessToken\n    refreshToken\n  }\n}")
                        put("variables", buildJsonObject {
                            put("input", buildJsonObject {
                                put("username", user)
                                put("password", pass)
                            })
                        })
                        put("operationName", "LOGIN")
                    }.toString().toRequestBody(contentType)
                    val response = baseClient.newCall(
                        POST(
                            "$baseUrl/api/graphql",
                            body = requestBody,
                        ),
                    ).awaitSuccess().parseAs<GraphQlResult<LoginPayload1>>()
                    if (response.errors?.let { it.size > 0 } == true) {
                        Log.w(TAG, "Invalid credentials: ${response.errors[0].message}")
                    }

                    currentToken = response.data!!.login.accessToken
                    refreshToken = response.data!!.login.refreshToken
                }
            }
            else -> {}
        }
    }

    companion object {
        private const val TAG = "Suwayomi.TokenManager"
    }
}

