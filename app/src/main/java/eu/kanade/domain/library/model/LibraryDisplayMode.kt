package eu.kanade.domain.library.model

import eu.kanade.domain.category.model.Category

sealed class LibraryDisplayMode(
    override val flag: Long,
) : FlagWithMask {

    override val mask: Long = 0b00000011L

    object CompactGrid : LibraryDisplayMode(0b00000000)
    object ComfortableGrid : LibraryDisplayMode(0b00000001)
    object List : LibraryDisplayMode(0b00000010)
    object CoverOnlyGrid : LibraryDisplayMode(0b00000011)

    object Serializer {
        fun deserialize(serialized: String): LibraryDisplayMode {
            return LibraryDisplayMode.deserialize(serialized)
        }

        fun serialize(value: LibraryDisplayMode): String {
            return value.serialize()
        }
    }

    companion object {
        val values = setOf(CompactGrid, ComfortableGrid, List, CoverOnlyGrid)
        val default = CompactGrid

        fun valueOf(flag: Long?): LibraryDisplayMode {
            if (flag == null) return default
            return values
                .find { mode -> mode.flag == flag and mode.mask }
                ?: default
        }

        fun deserialize(serialized: String): LibraryDisplayMode {
            return when (serialized) {
                "COMFORTABLE_GRID" -> ComfortableGrid
                "COMPACT_GRID" -> CompactGrid
                "COVER_ONLY_GRID" -> CoverOnlyGrid
                "LIST" -> List
                else -> default
            }
        }
    }

    fun serialize(): String {
        return when (this) {
            ComfortableGrid -> "COMFORTABLE_GRID"
            CompactGrid -> "COMPACT_GRID"
            CoverOnlyGrid -> "COVER_ONLY_GRID"
            List -> "LIST"
        }
    }
}

val Category.display: LibraryDisplayMode
    get() = LibraryDisplayMode.valueOf(flags)
