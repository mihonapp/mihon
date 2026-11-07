package mihon.domain.source.service

import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.seconds

/** Allow slow source startup, but report a stalled response as a recoverable network failure. */
suspend fun <T : Any> awaitSourceResponse(block: suspend () -> T): T =
    withTimeoutOrNull(60.seconds) { block() }
        ?: throw SocketTimeoutException("Source did not respond within 60 seconds")
