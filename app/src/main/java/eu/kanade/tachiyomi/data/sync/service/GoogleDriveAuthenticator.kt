package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.tachiyomi.util.system.openInBrowser
import tachiyomi.domain.sync.service.SyncPreferences
import tachiyomi.domain.sync.service.SyncService
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Drives the whole sign-in: opens the consent screen, catches the redirect on a loopback port and
 * trades the authorization code for tokens.
 */
class GoogleDriveAuthenticator(
    private val context: Context,
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val api: GoogleDriveApi = GoogleDriveApi(),
) {

    suspend fun signIn() {
        if (!syncPreferences.hasGoogleDriveCredentials()) {
            throw SyncAuthException("Enter the OAuth client ID and secret first")
        }

        // The listener has to be up before the browser opens, and torn down whatever happens
        LoopbackRedirectServer().use { server ->
            val codes = GoogleDriveApi.generatePkceCodes()

            context.openInBrowser(
                api.authUrl(redirectUri = server.redirectUri, codeChallenge = codes.codeChallenge),
                forceDefaultBrowser = true,
            )

            val authCode = server.awaitAuthorizationCode()
            api.exchangeAuthorizationCode(
                authCode = authCode,
                codeVerifier = codes.codeVerifier,
                redirectUri = server.redirectUri,
            )
        }

        syncPreferences.syncService.set(SyncService.GOOGLE_DRIVE.ordinal)
    }
}
