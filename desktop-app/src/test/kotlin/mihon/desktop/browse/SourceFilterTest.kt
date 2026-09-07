package mihon.desktop.browse

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import mihon.desktop.extension.builtin.BundledMangaDexSource
import mihon.extension.source.model.Filter
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class SourceFilterTest {

    @Test
    fun `getFilterList returns populated filters for MangaDex`() {
        val source = BundledMangaDexSource(OkHttpClient())
        val filterList = source.getFilterList()

        filterList.shouldNotBeNull()
        val filters = filterList.filters
        filters.size shouldBe 4

        val sort = filters[0].shouldBeInstanceOf<BundledMangaDexSource.SortFilter>()
        sort.state?.index shouldBe 0
        sort.state?.ascending shouldBe false

        val contentRating = filters[1].shouldBeInstanceOf<BundledMangaDexSource.ContentRatingGroup>()
        contentRating.state.shouldHaveSize(4)

        val status = filters[2].shouldBeInstanceOf<BundledMangaDexSource.StatusGroup>()
        status.state.shouldHaveSize(4)

        val lang = filters[3].shouldBeInstanceOf<BundledMangaDexSource.OriginalLanguageFilter>()
        lang.state shouldBe 0
    }

    @Test
    fun `filter state modifications reflect correctly`() {
        val source = BundledMangaDexSource(OkHttpClient())
        val filterList = source.getFilterList()
        val filters = filterList.filters

        val sort = filters[0] as BundledMangaDexSource.SortFilter
        sort.state = Filter.Sort.Selection(index = 2, ascending = true)
        sort.state?.index shouldBe 2
        sort.state?.ascending shouldBe true

        val contentRating = filters[1] as BundledMangaDexSource.ContentRatingGroup
        contentRating.state[0].state = true
        contentRating.state[1].state = true
        contentRating.state[0].state shouldBe true

        val lang = filters[3] as BundledMangaDexSource.OriginalLanguageFilter
        lang.state = 1
        lang.values[lang.state] shouldBe "Japanese"
    }
}
