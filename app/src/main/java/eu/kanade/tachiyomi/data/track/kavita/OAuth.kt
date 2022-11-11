package eu.kanade.tachiyomi.data.track.kavita

class OAuth(
    val authentications: List<SourceAuth> = listOf<SourceAuth>(
        SourceAuth(1),
        SourceAuth(2),
        SourceAuth(3),
    ),
) {

    fun getToken(apiUrl: String): String? {
        for (authentication in authentications) {
            if (authentication.apiUrl == apiUrl) {
                return authentication.jwtToken
            }
        }
        return null
    }
}
