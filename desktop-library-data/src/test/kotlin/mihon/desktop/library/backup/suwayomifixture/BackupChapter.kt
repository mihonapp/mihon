// Adapted from Suwayomi-Server d10e000e1fdcac6f3c84d002f0b459c90c1b00f3 (MPL-2.0).
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.desktop.library.backup.suwayomifixture

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Scalars need defaults (proto3 peers omit zero values); see BackupManga.
@Serializable
data class BackupChapter(
    // in 1.x some of these values have different names
    // url is called key in 1.x
    @ProtoNumber(1) @EncodeDefault var url: String = "",
    @ProtoNumber(2) @EncodeDefault var name: String = "",
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    // lastPageRead is called progress in 1.x
    @ProtoNumber(6) var lastPageRead: Int = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    // chapterNumber is called number is 1.x
    @ProtoNumber(9) var chapterNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Int = 0,
    // syncyomi
    @ProtoNumber(11) var lastModifiedAt: Long = 0,
    @ProtoNumber(12) var version: Long = 0,
    @ProtoNumber(13) var memo: ByteArray = byteArrayOf(123, 125),
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
)
