package exh.metadata.models

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.RealmClass

@RealmClass
open class HitomiPage: RealmObject() {
    @Index lateinit var gallery: String

    @Index var index: Int = -1

    lateinit var url: String
}
