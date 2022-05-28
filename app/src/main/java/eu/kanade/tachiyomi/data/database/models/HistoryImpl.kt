package eu.kanade.tachiyomi.data.database.models

/**
 * Object containing the history statistics of a chapter
 */
class HistoryImpl : History {

    /**
     * Id of history object.
     */
    override var id: Long? = null

    /**
     * Chapter id of history object.
     */
    override var chapter_id: Long = 0

    /**
     * Last time chapter was read in time long format
     */
    override var last_read: Long = 0

    /**
     * Total time chapter was read
     */
    override var time_read: Long = 0
}
