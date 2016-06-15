package eu.kanade.tachiyomi.data.database.models

import java.io.Serializable

/**
 * Object containing the history statistics of a chapter
 */
class History : Serializable {

    /**
     * Id of history object.
     */
    var id: Long? = null

    /**
     * Chapter id of history object.
     */
    var chapter_id: Long = 0

    /**
     * Last time chapter was read in time long format
     */
    var last_read: Long = 0

    /**
     * Total time chapter was read - todo not yet implemented
     */
    var time_read: Long = 0

    companion object {

        /**
         * History constructor
         *
         * @param chapter chapter object
         * @return history object
         */
        fun create(chapter: Chapter): History {
            val history = History()
            history.chapter_id = chapter.id!!
            return history
        }
    }
}
