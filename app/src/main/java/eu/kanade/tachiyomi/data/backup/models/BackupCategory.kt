package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.category.model.Category

@Serializable
class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Long = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    @ProtoNumber(100) var flags: Long = 0,
) {
    fun toCategory(id: Long) = Category(
        id = id,
        name = this@BackupCategory.name,
        flags = this@BackupCategory.flags,
        order = this@BackupCategory.order,
    )
}

val backupCategoryMapper = { category: Category ->
    BackupCategory(
        name = category.name,
        order = category.order,
        flags = category.flags,
    )
}
