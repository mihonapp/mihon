package exh.log

import com.elvishew.xlog.XLog
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

fun OkHttpClient.Builder.maybeInjectEHLogger(): OkHttpClient.Builder {
    if(EHLogLevel.shouldLog(EHLogLevel.EXTREME)) {
        val xLogger = XLog.tag("EHNetwork")
                .nst()
        val interceptor = HttpLoggingInterceptor {
            xLogger.d(it)
        }
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        return addInterceptor(interceptor)
    }
    return this
}
