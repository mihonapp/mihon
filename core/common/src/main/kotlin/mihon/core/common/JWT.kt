package mihon.core.common

import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.Default.UrlSafe

object JWT {
    @PublishedApi
    internal val base64 = UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    inline fun <reified T> decode(token: String): T {
        val claims = base64.decode(token.split(".")[1]).decodeToString()
        return json.decodeFromString<T>(claims)
    }
}
