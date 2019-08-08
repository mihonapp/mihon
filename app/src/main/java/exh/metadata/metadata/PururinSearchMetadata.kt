package exh.metadata.metadata

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata

class PururinSearchMetadata : RaisedSearchMetadata() {
    var prId: Int? = null

    var prShortLink: String? = null

    var title by titleDelegate(TITLE_TYPE_TITLE)
    var altTitle by titleDelegate(TITLE_TYPE_ALT_TITLE)

    var thumbnailUrl: String? = null

    var artist: String? = null
    var artistDisp: String? = null

    var circle: String? = null
    var circleDisp: String? = null

    var parody: String? = null // TODO Mult
    var parodyDisp: String? = null

    var character: String? = null // TODO Mult
    var characterDisp: String? = null

    var category: String? = null
    var categoryDisp: String? = null

    var collection: String? = null
    var collectionDisp: String? = null

    var language: String? = null
    var languageDisp: String? = null

    var uploadDisp: String? = null

    var pages: Int? = null

    var fileSize: String? = null

    var ratingCount: Int? = null
    var averageRating: Double? = null

    override fun copyTo(manga: SManga) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        private const val TITLE_TYPE_TITLE = 0
        private const val TITLE_TYPE_ALT_TITLE = 1

        const val TAG_TYPE_CONTENTS = 0

        val BASE_URL = "https://pururin.io"
    }
}