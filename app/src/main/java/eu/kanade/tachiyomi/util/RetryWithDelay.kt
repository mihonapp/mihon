package eu.kanade.tachiyomi.util

import rx.Observable
import rx.functions.Func1
import java.util.concurrent.TimeUnit.MILLISECONDS

class RetryWithDelay(
        private val maxRetries: Int = 1,
        private val retryStrategy: (Int) -> Int = { 1000 }
) : Func1<Observable<out Throwable>, Observable<*>> {

    private var retryCount = 0

    override fun call(attempts: Observable<out Throwable>) = attempts.flatMap { error ->
        val count = ++retryCount
        if (count <= maxRetries) {
            Observable.timer(retryStrategy(count).toLong(), MILLISECONDS)
        } else {
            Observable.error(error as Throwable)
        }
    }
}