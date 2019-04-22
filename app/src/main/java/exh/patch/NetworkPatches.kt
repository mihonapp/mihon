package exh.patch

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

typealias EHInterceptor = (request: Request, response: Response, sourceId: Long) -> Response

fun OkHttpClient.Builder.injectPatches(sourceIdProducer: () -> Long): OkHttpClient.Builder {
    return addInterceptor { chain ->
        val req = chain.request()
        val response = chain.proceed(req)
        val sourceId = sourceIdProducer()
        findAndApplyPatches(sourceId)(req, response, sourceId)
    }
}

fun findAndApplyPatches(sourceId: Long): EHInterceptor {
    return ((EH_INTERCEPTORS[sourceId] ?: emptyList()) +
            (EH_INTERCEPTORS[EH_UNIVERSAL_INTERCEPTOR] ?: emptyList())).merge()
}

fun List<EHInterceptor>.merge(): EHInterceptor {
    return { request, response, sourceId ->
        fold(response) { acc, int ->
            int(request, acc, sourceId)
        }
    }
}

private const val EH_UNIVERSAL_INTERCEPTOR = -1L
private val EH_INTERCEPTORS: Map<Long, List<EHInterceptor>> = mapOf(
        EH_UNIVERSAL_INTERCEPTOR to listOf(
                CAPTCHA_DETECTION_PATCH // Auto captcha detection
        ),

        // MangaDex login support
        *MANGADEX_SOURCE_IDS.map { id ->
            id to listOf(MANGADEX_LOGIN_PATCH)
        }.toTypedArray()
)
