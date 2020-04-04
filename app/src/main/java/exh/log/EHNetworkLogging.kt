package exh.log

import okhttp3.OkHttpClient

fun OkHttpClient.Builder.maybeInjectEHLogger(): OkHttpClient.Builder { // TODO - un-break this
/*    if(false &&EHLogLevel.shouldLog(EHLogLevel.EXTREME)) {
        val xLogger = XLog.tag("EHNetwork")
                .nst()
        val interceptor = HttpLoggingInterceptor {
            xLogger.d(it)
        }
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        return addInterceptor(interceptor)
    } */
    return this
}
