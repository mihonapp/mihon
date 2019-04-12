package exh.favorites

import exh.metadata.metadata.EHentaiSearchMetadata
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import java.util.*

@RealmClass
open class FavoriteEntry : RealmObject() {
    @PrimaryKey var id: String = UUID.randomUUID().toString()

    var title: String? = null

    @Index lateinit var gid: String

    @Index lateinit var token: String

    @Index var category: Int = -1

    fun getUrl() = EHentaiSearchMetadata.idAndTokenToUrl(gid, token)
}
