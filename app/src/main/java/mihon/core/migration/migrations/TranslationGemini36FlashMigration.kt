package mihon.core.migration.migrations

import android.app.Application
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.io.File

/**
 * Migrates the translation pipeline to the stable Gemini 3.6 Flash contract.
 *
 * This intentionally clears setup/model discovery state. The old setup result
 * covered sampling and inpaint capabilities that are no longer part of the
 * request contract, so reusing it could incorrectly mark the new pipeline as
 * ready.
 */
class TranslationGemini36FlashMigration : Migration {
    override val version: Float = 23f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val application = migrationContext.get<Application>() ?: return false

        migratePreferences(preferenceStore)
        File(application.filesDir, INPAINT_DIRECTORY).deleteRecursively()
        return true
    }

    internal companion object {
        const val MODEL = "gemini-3.6-flash"
        const val INPAINT_DIRECTORY = "translations/inpaint"

        fun migratePreferences(preferenceStore: PreferenceStore) {
            preferenceStore.getString(MODEL_KEY).set(MODEL)

            listOf(
                Preference.privateKey("translation_setup_fingerprint"),
                "translation_setup_tested_translation_model",
                "translation_setup_status",
                "translation_setup_message",
                "translation_cached_models_json",
                "translation_gemini_inpaint_model",
                "translation_enable_inpaint",
                "translation_thinking_level",
                "translation_raw_json_override",
                "translation_parallel_retry_lanes",
            ).forEach { key ->
                preferenceStore.getString(key).delete()
            }

            listOf(
                "translation_temperature",
                "translation_top_p",
            ).forEach { key -> preferenceStore.getFloat(key).delete() }
            listOf(
                "translation_top_k",
                "translation_concurrency",
                "translation_max_images_per_batch",
            ).forEach { key -> preferenceStore.getInt(key).delete() }
            preferenceStore.getLong("translation_setup_tested_at").delete()
            preferenceStore.getString("translation_setup_tested_inpaint_model").delete()
        }

        private const val MODEL_KEY = "translation_gemini_model"
    }
}
