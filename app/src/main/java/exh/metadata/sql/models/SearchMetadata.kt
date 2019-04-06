package exh.metadata.sql.models

data class SearchMetadata(
        // Manga ID this gallery is linked to
        val mangaId: Long,

        // Gallery uploader
        val uploader: String?,

        // Extra data attached to this metadata, in JSON format
        val extra: String,

        // Indexed extra data attached to this metadata
        val indexedExtra: String?,

        // The version of this metadata's extra. Used to track changes to the 'extra' field's schema
        val extraVersion: Int
) {
    // Transient information attached to this piece of metadata, useful for caching
    var transientCache: Map<String, Any>? = null
}