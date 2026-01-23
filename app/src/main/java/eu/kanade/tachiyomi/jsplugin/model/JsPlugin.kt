package eu.kanade.tachiyomi.jsplugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a JS plugin from LNReader-compatible repositories.
 * Maps directly to the plugin index JSON format.
 */
@Serializable
data class JsPlugin(
    val id: String,
    val name: String,
    val site: String,
    val lang: String,
    val version: String,
    val url: String,
    val iconUrl: String,
    val customCSS: String? = null,
    val customJS: String? = null,
    var repositoryUrl: String? = null,
) {
    /**
     * Unique identifier combining plugin ID and repository URL for disambiguation
     */
    fun uniqueId(): String = "js:$id"
    
    /**
     * Generate a stable Long ID for Source compatibility
     */
    fun sourceId(): Long {
        // Use same hashing approach as HttpSource for consistency
        val key = "${name.lowercase()}/$lang/js"
        return key.hashCode().toLong() and Long.MAX_VALUE
    }
    
    /**
     * Normalized language code for grouping (e.g., "English" -> "en")
     */
    fun langCode(): String = when {
        lang.contains("English", ignoreCase = true) -> "en"
        lang.contains("中文") || lang.contains("Chinese", ignoreCase = true) -> "zh"
        lang.contains("日本") || lang.contains("Japanese", ignoreCase = true) -> "ja"
        lang.contains("한국") || lang.contains("Korean", ignoreCase = true) -> "ko"
        lang.contains("Français", ignoreCase = true) -> "fr"
        lang.contains("Español", ignoreCase = true) -> "es"
        lang.contains("Português", ignoreCase = true) -> "pt"
        lang.contains("Русский", ignoreCase = true) -> "ru"
        lang.contains("Indonesia", ignoreCase = true) -> "id"
        lang.contains("Türkçe", ignoreCase = true) -> "tr"
        lang.contains("العربية") -> "ar"
        lang.contains("ไทย") -> "th"
        lang.contains("Việt", ignoreCase = true) -> "vi"
        lang.contains("Polski", ignoreCase = true) -> "pl"
        lang.contains("Українська", ignoreCase = true) -> "uk"
        lang.contains("Multi", ignoreCase = true) -> "all"
        else -> "other"
    }
}

/**
 * Represents a JS plugin repository
 */
@Serializable
data class JsPluginRepository(
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

/**
 * Installed JS plugin with cached code
 */
data class InstalledJsPlugin(
    val plugin: JsPlugin,
    val code: String,
    val installedVersion: String,
    val repositoryUrl: String,
)
