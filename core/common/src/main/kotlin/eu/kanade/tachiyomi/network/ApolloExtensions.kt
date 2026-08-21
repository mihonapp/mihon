package eu.kanade.tachiyomi.network

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloHttpException

/**
 * Similar to [okhttp3.Call.awaitSuccess] and wraps [ApolloHttpException] with [HttpException]
 */
suspend fun <D : Operation.Data> ApolloCall<D>.awaitSuccess(): ApolloResponse<D> {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    return try {
        execute()
    } catch (e: ApolloHttpException) {
        throw HttpException(e.statusCode).apply { stackTrace = callStack }
    }
}
