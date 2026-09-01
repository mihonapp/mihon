package mihon.reader.source

import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import java.io.Closeable

interface ChapterSource : Closeable {
    val asset: ReaderChapterAsset

    suspend fun pages(): List<PageDescriptor>

    suspend fun open(pageId: PageId): BoundedPageInput
}

fun interface ChapterSourceFactory {
    fun create(asset: ReaderChapterAsset): ChapterSource
}
