package eu.kanade.tachiyomi.network

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloHttpException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Returns the result of calling [transform] with [data] if the GraphQL response had data present without errors,
 * otherwise logs the failure under [errorLog] and falls back to [default].
 *
 * Any [ApolloHttpException] is re-thrown as [HttpException] after logging the response body.
 *
 * Other failures are logged and the result of calling [default] is returned.
 */
fun <D : Operation.Data, R> ApolloResponse<D>.dataOrElse(
    errorLog: String,
    default: () -> R,
    transform: (D) -> R,
): R {
    if (exception != null) {
        val e = exception!!
        if (e is ApolloHttpException) {
            val body = e.body?.use { it.readUtf8() }
            logcat(LogPriority.ERROR, throwable = e) { "$errorLog: $body" }
            throw HttpException(e.statusCode).apply { stackTrace = e.stackTrace }
        }
        logcat(LogPriority.ERROR, throwable = e) { errorLog }
    } else if (!errors.isNullOrEmpty()) {
        val errorMessages = errors!!.joinToString(separator = "\n  ", prefix = "\n  ") { it.message }
        logcat(LogPriority.ERROR) { "$errorLog: $errorMessages" }
    } else if (data != null) {
        return transform(data!!)
    }
    return default()
}
