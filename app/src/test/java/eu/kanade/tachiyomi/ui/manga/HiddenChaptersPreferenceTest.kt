package eu.kanade.tachiyomi.ui.manga

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.domain.library.service.LibraryPreferences

class HiddenChaptersPreferenceTest {
    @Test
    fun `hidden visibility defaults off and survives recreating preferences`() {
        val values = mutableMapOf<String, Boolean>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val storage = mockk<SharedPreferences>(relaxed = true)
        every { storage.getBoolean(any(), any()) } answers { values[firstArg<String>()] ?: secondArg<Boolean>() }
        every { storage.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg<String>()] = secondArg<Boolean>()
            editor
        }
        val original = LibraryPreferences(AndroidPreferenceStore(storage))
        assertFalse(original.hideHiddenChapters.defaultValue())
        assertFalse(original.hideHiddenChapters.get())
        original.hideHiddenChapters.set(true)
        val reopened = LibraryPreferences(AndroidPreferenceStore(storage))
        assertTrue(reopened.hideHiddenChapters.get())
        reopened.hideHiddenChapters.set(false)
        assertFalse(LibraryPreferences(AndroidPreferenceStore(storage)).hideHiddenChapters.get())
    }
}
