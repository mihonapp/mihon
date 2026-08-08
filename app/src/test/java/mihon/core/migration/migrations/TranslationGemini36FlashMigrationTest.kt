package mihon.core.migration.migrations

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class TranslationGemini36FlashMigrationTest {

    @Test
    fun `migration forces model and clears obsolete translation state`() {
        val store = mockk<PreferenceStore>()
        val deletedKeys = mutableSetOf<String>()
        val stringPreferences = mutableMapOf<String, Preference<String>>()
        val intPreferences = mutableMapOf<String, Preference<Int>>()
        val floatPreferences = mutableMapOf<String, Preference<Float>>()
        val longPreferences = mutableMapOf<String, Preference<Long>>()

        every { store.getString(any(), any()) } answers {
            val key = firstArg<String>()
            stringPreferences.getOrPut(key) {
                mockk<Preference<String>>(relaxed = true).also { preference ->
                    every { preference.delete() } answers { deletedKeys += key }
                }
            }
        }
        every { store.getInt(any(), any()) } answers {
            val key = firstArg<String>()
            intPreferences.getOrPut(key) {
                mockk<Preference<Int>>(relaxed = true).also { preference ->
                    every { preference.delete() } answers { deletedKeys += key }
                }
            }
        }
        every { store.getFloat(any(), any()) } answers {
            val key = firstArg<String>()
            floatPreferences.getOrPut(key) {
                mockk<Preference<Float>>(relaxed = true).also { preference ->
                    every { preference.delete() } answers { deletedKeys += key }
                }
            }
        }
        every { store.getLong(any(), any()) } answers {
            val key = firstArg<String>()
            longPreferences.getOrPut(key) {
                mockk<Preference<Long>>(relaxed = true).also { preference ->
                    every { preference.delete() } answers { deletedKeys += key }
                }
            }
        }

        TranslationGemini36FlashMigration.migratePreferences(store)

        verify { stringPreferences["translation_gemini_model"]!!.set("gemini-3.6-flash") }
        assertTrue("translation_temperature" in deletedKeys)
        assertTrue("translation_top_p" in deletedKeys)
        assertTrue("translation_top_k" in deletedKeys)
        assertTrue("translation_raw_json_override" in deletedKeys)
        assertTrue("translation_gemini_inpaint_model" in deletedKeys)
        assertTrue("translation_max_images_per_batch" in deletedKeys)
    }
}
