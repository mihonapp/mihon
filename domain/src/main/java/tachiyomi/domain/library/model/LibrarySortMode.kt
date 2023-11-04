package tachiyomi.domain.library.model

import tachiyomi.domain.category.model.Category

data class LibrarySort(
    val type: Type,
    val direction: Direction,
) : FlagWithMask {

    override val flag: Long
        get() = type + direction

    override val mask: Long
        get() = type.mask or direction.mask

    val isAscending: Boolean
        get() = direction == Direction.Ascending

    sealed class Type(
        override val flag: Long,
    ) : FlagWithMask {

        override val mask: Long = 0b00111100L

        data object Alphabetical : Type(0b00000000)
        data object LastRead : Type(0b00000100)
        data object LastUpdate : Type(0b00001000)
        data object UnreadCount : Type(0b00001100)
        data object TotalChapters : Type(0b00010000)
        data object LatestChapter : Type(0b00010100)
        data object ChapterFetchDate : Type(0b00011000)
        data object DateAdded : Type(0b00011100)
        data object TrackerMean : Type(0b000100000)

        companion object {
            fun valueOf(flag: Long): Type {
                return types.find { type -> type.flag == flag and type.mask } ?: default.type
            }
        }
    }

    sealed class Direction(
        override val flag: Long,
    ) : FlagWithMask {

        override val mask: Long = 0b01000000L

        data object Ascending : Direction(0b01000000)
        data object Descending : Direction(0b00000000)

        companion object {
            fun valueOf(flag: Long): Direction {
                return directions.find { direction -> direction.flag == flag and direction.mask } ?: default.direction
            }
        }
    }

    object Serializer {
        fun deserialize(serialized: String): LibrarySort {
            return LibrarySort.deserialize(serialized)
        }

        fun serialize(value: LibrarySort): String {
            return value.serialize()
        }
    }

    companion object {
        val types by lazy {
            setOf(
                Type.Alphabetical,
                Type.LastRead,
                Type.LastUpdate,
                Type.UnreadCount,
                Type.TotalChapters,
                Type.LatestChapter,
                Type.ChapterFetchDate,
                Type.DateAdded,
                Type.TrackerMean,
            )
        }
        val directions by lazy { setOf(Direction.Ascending, Direction.Descending) }
        val default = LibrarySort(Type.Alphabetical, Direction.Ascending)

        fun valueOf(flag: Long?): LibrarySort {
            if (flag == null) return default
            return LibrarySort(
                Type.valueOf(flag),
                Direction.valueOf(flag),
            )
        }

        fun deserialize(serialized: String): LibrarySort {
            if (serialized.isEmpty()) return default
            return try {
                val values = serialized.split(",")
                val type = when (values[0]) {
                    "ALPHABETICAL" -> Type.Alphabetical
                    "LAST_READ" -> Type.LastRead
                    "LAST_MANGA_UPDATE" -> Type.LastUpdate
                    "UNREAD_COUNT" -> Type.UnreadCount
                    "TOTAL_CHAPTERS" -> Type.TotalChapters
                    "LATEST_CHAPTER" -> Type.LatestChapter
                    "CHAPTER_FETCH_DATE" -> Type.ChapterFetchDate
                    "DATE_ADDED" -> Type.DateAdded
                    "TRACKER_MEAN" -> Type.TrackerMean
                    else -> Type.Alphabetical
                }
                val ascending = if (values[1] == "ASCENDING") Direction.Ascending else Direction.Descending
                LibrarySort(type, ascending)
            } catch (e: Exception) {
                default
            }
        }
    }

    fun serialize(): String {
        val type = when (type) {
            Type.Alphabetical -> "ALPHABETICAL"
            Type.LastRead -> "LAST_READ"
            Type.LastUpdate -> "LAST_MANGA_UPDATE"
            Type.UnreadCount -> "UNREAD_COUNT"
            Type.TotalChapters -> "TOTAL_CHAPTERS"
            Type.LatestChapter -> "LATEST_CHAPTER"
            Type.ChapterFetchDate -> "CHAPTER_FETCH_DATE"
            Type.DateAdded -> "DATE_ADDED"
            Type.TrackerMean -> "TRACKER_MEAN"
        }
        val direction = if (direction == Direction.Ascending) "ASCENDING" else "DESCENDING"
        return "$type,$direction"
    }
}

val Category?.sort: LibrarySort
    get() = LibrarySort.valueOf(this?.flags)
