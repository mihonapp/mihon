package eu.kanade.tachiyomi.data.database.models

class LibraryManga : MangaImpl() {

    var unreadCount: Int = 0
    var readCount: Int = 0

    val totalChapters
        get() = readCount + unreadCount

    val hasStarted
        get() = readCount > 0

    var category: Int = 0
}
