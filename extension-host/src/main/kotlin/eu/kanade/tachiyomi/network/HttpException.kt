package eu.kanade.tachiyomi.network

import okhttp3.Response

class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
