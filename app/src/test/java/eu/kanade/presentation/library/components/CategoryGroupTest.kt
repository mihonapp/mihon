package eu.kanade.presentation.library.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

class CategoryGroupTest {

    private fun makeCategory(id: Long, name: String): Category {
        return Category(
            id = id,
            name = name,
            order = id,
            flags = 0L,
        )
    }

    @Test
    fun `no slash categories are all top level`() {
        val categories = listOf(
            makeCategory(1, "Action"),
            makeCategory(2, "Romance"),
            makeCategory(3, "Comedy"),
        )
        val groups = buildCategoryGroups(categories)

        assertEquals(3, groups.size)
        assertTrue(groups.all { it.isTopLevel })
        assertEquals("Action", groups[0].parentName)
        assertEquals("Romance", groups[1].parentName)
        assertEquals("Comedy", groups[2].parentName)
    }

    @Test
    fun `slash categories are grouped by parent`() {
        val categories = listOf(
            makeCategory(1, "Shounen/SF"),
            makeCategory(2, "Shounen/Battle"),
            makeCategory(3, "Shounen/Sports"),
        )
        val groups = buildCategoryGroups(categories)

        assertEquals(1, groups.size)
        assertFalse(groups[0].isTopLevel)
        assertEquals("Shounen", groups[0].parentName)
        assertEquals(3, groups[0].children.size)
        assertEquals("SF", groups[0].children[0].childName)
        assertEquals("Battle", groups[0].children[1].childName)
        assertEquals("Sports", groups[0].children[2].childName)
    }

    @Test
    fun `mixed slash and non-slash categories preserve order`() {
        val categories = listOf(
            makeCategory(1, "Favorites"),
            makeCategory(2, "Shounen/SF"),
            makeCategory(3, "Shounen/Battle"),
            makeCategory(4, "Completed"),
            makeCategory(5, "Shoujo/Romance"),
            makeCategory(6, "Shoujo/Fantasy"),
        )
        val groups = buildCategoryGroups(categories)

        assertEquals(4, groups.size)
        assertTrue(groups[0].isTopLevel)
        assertEquals("Favorites", groups[0].parentName)

        assertFalse(groups[1].isTopLevel)
        assertEquals("Shounen", groups[1].parentName)
        assertEquals(2, groups[1].children.size)

        assertTrue(groups[2].isTopLevel)
        assertEquals("Completed", groups[2].parentName)

        assertFalse(groups[3].isTopLevel)
        assertEquals("Shoujo", groups[3].parentName)
        assertEquals(2, groups[3].children.size)
    }

    @Test
    fun `single child in group is treated as top level`() {
        val categories = listOf(
            makeCategory(1, "Shounen/SF"),
            makeCategory(2, "Action"),
        )
        val groups = buildCategoryGroups(categories)

        assertEquals(2, groups.size)
        assertTrue(groups[0].isTopLevel)
        assertTrue(groups[1].isTopLevel)
    }

    @Test
    fun `edge case leading slash treated as top level`() {
        val categories = listOf(
            makeCategory(1, "/SF"),
            makeCategory(2, "Action"),
        )
        val groups = buildCategoryGroups(categories)

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.isTopLevel })
    }

    @Test
    fun `edge case trailing slash treated as top level`() {
        val categories = listOf(
            makeCategory(1, "Shounen/"),
            makeCategory(2, "Action"),
        )
        val groups = buildCategoryGroups(categories)

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.isTopLevel })
    }

    @Test
    fun `multiple slashes split on first only`() {
        val categories = listOf(
            makeCategory(1, "Shounen/SF/Hard"),
            makeCategory(2, "Shounen/SF/Soft"),
        )
        val groups = buildCategoryGroups(categories)

        assertEquals(1, groups.size)
        assertFalse(groups[0].isTopLevel)
        assertEquals("Shounen", groups[0].parentName)
        assertEquals("SF/Hard", groups[0].children[0].childName)
        assertEquals("SF/Soft", groups[0].children[1].childName)
    }

    @Test
    fun `empty categories list returns empty`() {
        val groups = buildCategoryGroups(emptyList())
        assertEquals(0, groups.size)
    }
}
