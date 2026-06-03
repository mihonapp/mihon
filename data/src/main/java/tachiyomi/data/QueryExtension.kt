package tachiyomi.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T : Any> Query<T>.subscribeToList(
    context: CoroutineContext = EmptyCoroutineContext,
): Flow<List<T>> = asFlow().map {
    withContext(context) {
        it.awaitAsList()
    }
}

fun <T : Any> Query<T>.subscribeToOne(
    context: CoroutineContext = EmptyCoroutineContext,
): Flow<T> = asFlow().map {
    withContext(context) {
        it.awaitAsOne()
    }
}

fun <T : Any> Query<T>.subscribeToOneOrNull(
    context: CoroutineContext = EmptyCoroutineContext,
): Flow<T?> = asFlow().map {
    withContext(context) {
        it.awaitAsOneOrNull()
    }
}
