package tachiyomi.domain.download.model

// This class represents a (possibly) downloaded chapter. It has
// enough information to check whether the chapter it represents has
// been downloaded, and if so, where it is stored on the filesystem.
// Use this instead of Chapter when dealing with chapter downloads, to
// avoid requiring the caller to obtain extra information that isn't
// needed to locate chapter downloads.

data class ChapterDownload(
    val mangaTitle: String,
    val chapterName: String,
    val chapterScanlator: String?,
    val chapterUrl: String,
) {
    //
}
