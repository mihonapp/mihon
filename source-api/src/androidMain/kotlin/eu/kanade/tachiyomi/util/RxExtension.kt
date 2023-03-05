package eu.kanade.tachiyomi.util

import rx.Observable
import tachiyomi.core.util.lang.awaitSingle

actual suspend fun <T> Observable<T>.awaitSingle(): T = awaitSingle()
