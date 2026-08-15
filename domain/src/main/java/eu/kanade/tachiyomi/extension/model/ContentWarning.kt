package eu.kanade.tachiyomi.extension.model

/**
 * Content classification of an extension as a whole, as declared by the extension itself or by the
 * store it was listed in.
 *
 * Declared in order of increasing severity.
 */
enum class ContentWarning {
    /**
     * No classification was provided. Extensions from legacy stores, which only declare a boolean
     * NSFW flag, land here when that flag is unset.
     */
    UNSPECIFIED,

    /** Explicitly declared to serve no adult content. */
    SAFE,

    /** Mostly safe, but some of the content it serves is adult. */
    MIXED,

    /** Dedicated to adult content. */
    NSFW,
    ;

    /** Whether sources from this extension may serve adult content at all. */
    val hasAdultContent: Boolean
        get() = this == MIXED || this == NSFW
}
