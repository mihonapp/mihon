@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.database.models

class TrackImpl : Track {

    override var id: Long? = null

    override var manga_id: Long = 0

    override var tracker_id: Long = 0

    override var remote_id: Long = 0

    override var library_id: Long? = null

    override lateinit var title: String

    override var last_chapter_read: Double = 0.0

    override var total_chapters: Long = 0

    override var score: Double = 0.0

    override var status: Long = 0

    override var started_reading_date: Long = 0

    override var finished_reading_date: Long = 0

    override var tracking_url: String = ""
}
