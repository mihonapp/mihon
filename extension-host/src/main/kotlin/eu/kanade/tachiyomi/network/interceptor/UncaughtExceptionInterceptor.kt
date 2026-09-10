package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Converts unexpected extension interceptor failures into recoverable HTTP failures. */
class UncaughtExceptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (error: Exception) {
        if (error is IOException) throw error
        throw IOException(error)
    }
}
