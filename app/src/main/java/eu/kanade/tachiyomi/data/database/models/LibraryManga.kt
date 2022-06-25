package eu.kanade.tachiyomi.data.database.models

class LibraryManga : MangaImpl() {

    var unreadCount: Int = 0
    var readCount: Int = 0

    val totalChapters
        get() = readCount + unreadCount

    val hasStarted
        get() = readCount > 0

    var category: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LibraryManga) return false
        if (!super.equals(other)) return false

        if (unreadCount != other.unreadCount) return false
        if (readCount != other.readCount) return false
        if (category != other.category) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + unreadCount
        result = 31 * result + readCount
        result = 31 * result + category
        return result
    }
}
