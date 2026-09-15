package eu.kanade.tachiyomi.network

import mihon.extension.ipc.NetworkFailure
import mihon.extension.ipc.NetworkFailureProvider

class HttpException @JvmOverloads constructor(
    val code: Int,
    override val networkFailure: NetworkFailure? = null,
) : IllegalStateException("HTTP error $code"), NetworkFailureProvider
