package eu.kanade.presentation.more.settings

import eu.kanade.presentation.more.settings.Preference.PreferenceItem.TextPreference
import eu.kanade.presentation.more.settings.screen.SearchableSettings.HighlightKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

class PreferenceScreenTest {

    private val editCategories = TextPreference(title = "Edit categories")
    private val categoriesGroup = Preference.PreferenceGroup(
        title = "Categories",
        preferenceItems = listOf(editCategories),
    )
    private val updateCategories = TextPreference(title = "Categories")
    private val otherUpdateCategories = TextPreference(title = "Categories", subtitle = "Other")
    private val globalUpdateGroup = Preference.PreferenceGroup(
        title = "Global update",
        preferenceItems = listOf(updateCategories, otherUpdateCategories),
    )
    private val items = listOf(categoriesGroup, globalUpdateGroup)

    @Test
    fun `entries that share a title get different keys`() {
        val keys = buildList { items.forEachRow { _, key, row -> add(row to key) } }

        keys shouldBe listOf(
            0 to HighlightKey("Categories", isHeading = true),
            1 to HighlightKey("Edit categories"),
            // Row 2 is the spacer after the group
            3 to HighlightKey("Global update", isHeading = true),
            4 to HighlightKey("Categories"),
            5 to HighlightKey("Categories", occurrence = 1),
        )
    }

    @Test
    fun `finds only the entry identified by the key`() {
        val heading = items.findHighlighted(HighlightKey("Categories", isHeading = true))!!
        heading.index shouldBe 0
        heading.value shouldBeSameInstanceAs categoriesGroup

        val item = items.findHighlighted(HighlightKey("Categories"))!!
        item.index shouldBe 4
        item.value shouldBeSameInstanceAs updateCategories

        val secondItem = items.findHighlighted(HighlightKey("Categories", occurrence = 1))!!
        secondItem.index shouldBe 5
        secondItem.value shouldBeSameInstanceAs otherUpdateCategories

        items.findHighlighted(HighlightKey("Categories", occurrence = 2)) shouldBe null
    }

    @Test
    fun `hidden groups take no rows`() {
        val hiddenGroup = Preference.PreferenceGroup(
            title = "Hidden",
            visible = false,
            preferenceItems = listOf(TextPreference(title = "Categories")),
        )

        val item = (listOf(hiddenGroup) + items).findHighlighted(HighlightKey("Categories"))!!

        item.index shouldBe 4
        item.value shouldBeSameInstanceAs updateCategories
    }
}
