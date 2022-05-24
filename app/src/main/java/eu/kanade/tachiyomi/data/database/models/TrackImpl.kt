package eu.kanade.tachiyomi.data.database.models

class TrackImpl : Track {

    override var id: Long? = null

    override var manga_id: Long = 0

    override var sync_id: Int = 0

    override var media_id: Long = 0

    override var library_id: Long? = null

    override lateinit var title: String

    override var last_chapter_read: Float = 0F

    override var total_chapters: Int = 0

    override var score: Float = 0f

    override var status: Int = 0

    override var started_reading_date: Long = 0

    override var finished_reading_date: Long = 0

    override var tracking_url: String = ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrackImpl

        if (manga_id != other.manga_id) return false
        if (sync_id != other.sync_id) return false
        if (media_id != other.media_id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = manga_id.hashCode()
        result = 31 * result + sync_id
        result = 31 * result + media_id.hashCode()
        return result
    }
}
