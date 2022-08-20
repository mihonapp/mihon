package eu.kanade.tachiyomi.ui.library.setting

import eu.kanade.domain.category.model.Category

enum class SortDirectionSetting(val flag: Long) {
    ASCENDING(0b01000000),
    DESCENDING(0b00000000);

    companion object {
        const val MASK = 0b01000000L

        private fun fromFlag(flag: Long?): SortDirectionSetting {
            return values().find { mode -> mode.flag == flag } ?: ASCENDING
        }

        fun get(category: Category?): SortDirectionSetting {
            return fromFlag(category?.sortDirection)
        }
    }
}
