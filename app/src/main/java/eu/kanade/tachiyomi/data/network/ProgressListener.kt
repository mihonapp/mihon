package eu.kanade.tachiyomi.data.network

interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}