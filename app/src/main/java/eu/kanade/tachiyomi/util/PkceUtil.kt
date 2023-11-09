package eu.kanade.tachiyomi.util

import java.security.SecureRandom
import java.util.Base64

object PkceUtil {

    fun generateCodeVerifier(): String {
        val codeVerifier = ByteArray(50)
        SecureRandom().nextBytes(codeVerifier)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(codeVerifier)
    }
}
