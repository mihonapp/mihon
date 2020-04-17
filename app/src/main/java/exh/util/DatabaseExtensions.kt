package exh.util

import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetListOfObjects
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetObject
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutCollectionOfObjects
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> PreparedGetListOfObjects<T>.executeOnIO(): List<T> {
    return withContext(Dispatchers.IO) { executeAsBlocking() }
}

suspend fun <T> PreparedGetObject<T>.executeOnIO(): T? {
    return withContext(Dispatchers.IO) { executeAsBlocking() }
}

suspend fun <T> PreparedPutObject<T>.executeOnIO() {
    withContext(Dispatchers.IO) { executeAsBlocking() }
}

suspend fun <T> PreparedPutCollectionOfObjects<T>.executeOnIO() {
    withContext(Dispatchers.IO) { executeAsBlocking() }
}
