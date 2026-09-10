package android.util

object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        val decoder = if ((flags and URL_SAFE) != 0) {
            java.util.Base64.getUrlDecoder()
        } else {
            java.util.Base64.getDecoder()
        }
        val cleaned = str.replace("\n", "").replace("\r", "").trim()
        return try {
            decoder.decode(cleaned)
        } catch (_: IllegalArgumentException) {
            java.util.Base64.getMimeDecoder().decode(cleaned)
        }
    }

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray = decode(String(input), flags)

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).toByteArray()

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        val encoder = if ((flags and URL_SAFE) != 0) {
            if ((flags and NO_PADDING) !=
                0
            ) {
                java.util.Base64.getUrlEncoder().withoutPadding()
            } else {
                java.util.Base64.getUrlEncoder()
            }
        } else {
            if ((flags and NO_PADDING) !=
                0
            ) {
                java.util.Base64.getEncoder().withoutPadding()
            } else {
                java.util.Base64.getEncoder()
            }
        }
        val encoded = encoder.encodeToString(input)
        return if ((flags and NO_WRAP) != 0) encoded.replace("\n", "").replace("\r", "") else encoded
    }
}
