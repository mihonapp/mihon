package eu.kanade.tachiyomi.ui.reader.hiddenimage

data class HiddenImageSignature(
    val imageUrl: String?,
    val normalizedImageUrl: String?,
    val imageSha256: String?,
    val imageDhash: String?,
)
