package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.toBackupChapter
import io.kotest.matchers.shouldBe
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class BackupChapterTest {

    @Test
    fun `hidden state survives protobuf backup and restore independently of reading state`() {
        for (read in listOf(false, true)) {
            for (bookmark in listOf(false, true)) {
                for (hidden in listOf(false, true)) {
                    val chapter = Chapter.create().copy(
                        url = "/1",
                        name = "1",
                        read = read,
                        bookmark = bookmark,
                        hidden = hidden,
                        lastPageRead = 7,
                    )
                    val bytes = ProtoBuf.encodeToByteArray(BackupChapter.serializer(), chapter.toBackupChapter())
                    val restored = ProtoBuf.decodeFromByteArray(BackupChapter.serializer(), bytes).toChapterImpl()

                    restored.hidden shouldBe hidden
                    restored.read shouldBe read
                    restored.bookmark shouldBe bookmark
                    restored.lastPageRead shouldBe 7
                }
            }
        }
    }

    @Test
    fun `backup made before hidden flag defaults to visible and preserves reading state`() {
        // Protobuf fields: url=/1, name=1, read=true, bookmark=true; no hidden field.
        val legacyBytes = byteArrayOf(10, 2, 47, 49, 18, 1, 49, 32, 1, 40, 1)
        val restored = ProtoBuf.decodeFromByteArray(BackupChapter.serializer(), legacyBytes).toChapterImpl()

        restored.hidden shouldBe false
        restored.read shouldBe true
        restored.bookmark shouldBe true
    }
}
