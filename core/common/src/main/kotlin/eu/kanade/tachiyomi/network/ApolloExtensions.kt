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
 * This behavior can be replaced with a custom handler by providing a non-null value to [onException]. Such a lambda
 * MUST close the [ApolloHttpException.body] properly to avoid sockets and other resources leaking.
 *
 * Other failures are logged and the result of calling [default] is returned.
 *
 * @param errorLog    A log message which will be included with any exceptions or errors being logged.
 * @param default     A lambda which provides a default value to be returned in place of the expected value when an
 *                    exception or error is found.
 * @param onException A handler for [ApolloHttpException]s. It MUST handle closing the response body to avoid resource
 *                    leaks. Defaults to throwing a [HttpException] with the same status code.
 * @param transform   A transforming lambda for the GraphQL data received.
 */
fun <D : Operation.Data, R> ApolloResponse<D>.dataOrElse(
    errorLog: String,
    default: () -> R,
    onException: ((ApolloHttpException) -> Unit)? = null,
    transform: (D) -> R,
): R {
    if (exception != null) {
        val e = exception!!
        if (e is ApolloHttpException) {
            val body = e.body?.use { it.readUtf8() }
            logcat(LogPriority.ERROR, throwable = e) { "$errorLog: $body" }
            if (onException != null) {
                onException(e)
            } else {
                throw HttpException(e.statusCode).apply { stackTrace = e.stackTrace }
            }
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
