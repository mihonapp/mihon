package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationKeywordFilterTest {

    @Test
    fun `keywords accept comma semicolon pipe and newline separators`() {
        assertTrue(
            RecommendationKeywordFilter.parse("AI，3D; 机翻\nAI-generated|重口") ==
                listOf("ai", "3d", "机翻", "ai-generated", "重口"),
        )
    }

    @Test
    fun `ascii keyword uses word boundaries while CJK keyword uses substring matching`() {
        val maid = manga(title = "The Maid", genre = "romance")
        val ai = manga(title = "AI-generated collection", genre = "romance")
        val chinese = manga(title = "普通作品", genre = "AI生成, 校园")
        val terms = RecommendationKeywordFilter.parse("AI, AI生成")

        assertFalse(RecommendationKeywordFilter.matches(maid, terms))
        assertTrue(RecommendationKeywordFilter.matches(ai, terms))
        assertTrue(RecommendationKeywordFilter.matches(chinese, terms))
    }

    @Test
    fun `creator tags and description are all filtered case insensitively`() {
        val terms = RecommendationKeywordFilter.parse("blocked author, 3d")

        assertTrue(RecommendationKeywordFilter.matches(manga(author = "Blocked Author"), terms))
        assertTrue(RecommendationKeywordFilter.matches(manga(description = "Tags: 3D render"), terms))
    }

    private fun manga(
        title: String = "Title",
        author: String? = null,
        genre: String? = null,
        description: String? = null,
    ): SManga = SManga.create().apply {
        this.title = title
        this.author = author
        this.genre = genre
        this.description = description
    }
}
