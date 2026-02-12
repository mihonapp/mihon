package eu.kanade.domain.manga.model

data class HiddenImage(
    val id: Long,
    val mangaId: Long,
    val imageUrl: String?,
    val normalizedImageUrl: String?,
    val imageSha256: String?,
    val imageDhash: String?,
    val previewImage: ByteArray?,
    val scope: Scope,
    val createdAt: Long,
) {
    enum class Scope(val value: Long) {
        START(0),
        END(1),
        ANY(2),
    }
}

fun HiddenImage.Scope.appliesToPageIndex(
    pageIndex: Int,
    totalPages: Int,
    edgeWindowSize: Int,
): Boolean {
    val window = edgeWindowSize.coerceAtLeast(1)
    return when (this) {
        HiddenImage.Scope.ANY -> true
        HiddenImage.Scope.START -> pageIndex < window
        HiddenImage.Scope.END -> totalPages > 0 && pageIndex >= totalPages - window
    }
}

fun HiddenImage.Scope.next(): HiddenImage.Scope {
    return when (this) {
        HiddenImage.Scope.START -> HiddenImage.Scope.END
        HiddenImage.Scope.END -> HiddenImage.Scope.ANY
        HiddenImage.Scope.ANY -> HiddenImage.Scope.START
    }
}

fun HiddenImage.Scope.displayNameRes() = when (this) {
    HiddenImage.Scope.START -> tachiyomi.i18n.MR.strings.hidden_images_scope_start
    HiddenImage.Scope.END -> tachiyomi.i18n.MR.strings.hidden_images_scope_end
    HiddenImage.Scope.ANY -> tachiyomi.i18n.MR.strings.hidden_images_scope_any
}
