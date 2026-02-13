package eu.kanade.tachiyomi.ui.reader.hiddenimage

data class HiddenImageSignature(
    val imageSha256: String?,
    val imageDhash: String?,
    val previewImage: ByteArray?,
)
