package mihon.domain.migration.models

enum class MigrationFlag(val flag: Int) {
    CHAPTER(0b00001),
    CATEGORY(0b00010),

    // 0b00100 was used for manga trackers
    CUSTOM_COVER(0b01000),
    NOTES(0b100000),
    REMOVE_DOWNLOAD(0b10000),
    ;

    companion object {
        fun fromBit(bit: Int): Set<MigrationFlag> {
            return buildSet {
                entries.forEach { entry ->
                    if (bit and entry.flag != 0) add(entry)
                }
            }
        }

        fun toBit(flags: Set<MigrationFlag>): Int {
            return flags.map { it.flag }
                .reduceOrNull { acc, mask -> acc or mask }
                ?: 0
        }
    }
}
