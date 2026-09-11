package mihon.desktop.ui.browse

import java.util.Locale

object LocaleHelper {
    fun getSourceDisplayName(lang: String?): String {
        if (lang.isNullOrBlank()) return ""
        return when (lang.lowercase().trim()) {
            "all" -> "All"
            "other" -> "Other"
            "zh-hans", "zh-cn", "zh" -> "中文 (简体)"
            "zh-hant", "zh-tw", "zh-hk" -> "中文 (繁體)"
            "en" -> "English"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "es" -> "Español"
            "es-419", "es-la" -> "Español (Latinoamérica)"
            "fr" -> "Français"
            "de" -> "Deutsch"
            "it" -> "Italiano"
            "pt", "pt-br" -> "Português (Brasil)"
            "ru" -> "Русский"
            "id", "in" -> "Bahasa Indonesia"
            "vi" -> "Tiếng Việt"
            "th" -> "ไทย"
            "ar" -> "العربية"
            "tr" -> "Türkçe"
            "pl" -> "Polski"
            "uk" -> "Українська"
            else -> runCatching {
                val locale = Locale.forLanguageTag(lang)
                val displayName = locale.getDisplayName(locale)
                if (displayName.isNotBlank()) {
                    displayName.replaceFirstChar { it.uppercase(locale) }
                } else {
                    lang.uppercase()
                }
            }.getOrDefault(lang.uppercase())
        }
    }
}
