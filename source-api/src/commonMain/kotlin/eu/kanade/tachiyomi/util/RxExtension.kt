package eu.kanade.tachiyomi.util

import rx.Observable

expect suspend fun <T> Observable<T>.awaitSingle(): T
