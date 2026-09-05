package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test

class OnlineMangaDetailScreenTest {

    private val testSource = SourceDescriptor(
        id = 999L,
        name = "Online Source",
        lang = "en",
        className = "ext.test.OnlineSource",
    )

    private val testManga = SManga(
        url = "/manga/detail-test",
        title = "One Piece of Cake",
        author = "Oda-san",
        artist = "Oda-san",
        description = "A tale of pirates and cakes.",
        genre = listOf("Action", "Comedy"),
    )

    private val testChapter = SChapter(
        url = "/chapter/1",
        name = "Chapter 1: The Beginning",
        chapterNumber = 1f,
        scanlator = "ScanGroup",
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `displays manga metadata and chapter list, triggers add to library and read chapter`() = runComposeUiTest {
        var addedToLibrary = false
        var chapterToRead: SChapter? = null

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                OnlineMangaDetailScreen(
                    state = OnlineMangaDetailUiState(
                        source = testSource,
                        manga = testManga,
                        chapters = listOf(testChapter),
                        inLibrary = false,
                    ),
                    onBack = {},
                    onAddToLibrary = { addedToLibrary = true },
                    onReadChapter = { chapterToRead = it },
                )
            }
        }

        onNodeWithText("One Piece of Cake").assertIsDisplayed()
        onNodeWithText("Author: Oda-san").assertIsDisplayed()
        onNodeWithText("Genres: Action, Comedy").assertIsDisplayed()
        onNodeWithText("Chapters (1)").assertIsDisplayed()
        onNodeWithText("Chapter 1: The Beginning").assertIsDisplayed()

        // Click Add to Library
        onNodeWithTag("add-to-library-btn").performClick()
        addedToLibrary shouldBe true

        // Click Read Chapter
        onNodeWithTag("read-chapter-btn-/chapter/1").performClick()
        chapterToRead shouldBe testChapter
    }
}
