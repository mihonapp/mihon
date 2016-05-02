package eu.kanade.tachiyomi.data.database

import com.pushtorefresh.storio.sqlite.StorIOSQLite

inline fun StorIOSQLite.inTransaction(block: () -> Unit) {
    internal().beginTransaction()
    try {
        block()
        internal().setTransactionSuccessful()
    } finally {
        internal().endTransaction()
    }
}

inline fun <T> StorIOSQLite.inTransactionReturn(block: () -> T): T {
    internal().beginTransaction()
    try {
        val result = block()
        internal().setTransactionSuccessful()
        return result
    } finally {
        internal().endTransaction()
    }
}

