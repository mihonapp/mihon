package tachiyomi.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.core.common.util.lang.pausedWhileUnsubscribed
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T : Any> Query<T>.subscribeToList(
    context: CoroutineContext = EmptyCoroutineContext,
): Flow<List<T>> = asFlow().pausedWhileUnsubscribed().mapToList(context)

fun <T : Any> Query<T>.subscribeToOne(
    context: CoroutineContext = EmptyCoroutineContext,
): Flow<T> = asFlow().pausedWhileUnsubscribed().mapToOne(context)

fun <T : Any> Query<T>.subscribeToOneOrNull(
    context: CoroutineContext = EmptyCoroutineContext,
): Flow<T?> = asFlow().pausedWhileUnsubscribed().mapToOneOrNull(context)
