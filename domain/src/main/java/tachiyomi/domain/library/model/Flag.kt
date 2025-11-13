package tachiyomi.domain.library.model

interface Flag {
    val flag: Long
}

interface Mask {
    val mask: Long
}

interface FlagWithMask : Flag, Mask

operator fun Long.contains(other: Flag): Boolean {
    return if (other is Mask) {
        other.flag == this and other.mask
    } else {
        other.flag == this
    }
}

operator fun Long.plus(other: Flag): Long {
    return if (other is Mask) {
        this and other.mask.inv() or (other.flag and other.mask)
    } else {
        this or other.flag
    }
}

operator fun Flag.plus(other: Flag): Long {
    return if (other is Mask) {
        this.flag and other.mask.inv() or (other.flag and other.mask)
    } else {
        this.flag or other.flag
    }
}
