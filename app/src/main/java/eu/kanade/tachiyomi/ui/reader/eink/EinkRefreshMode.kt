package eu.kanade.tachiyomi.ui.reader.eink

/**
 * Semantic e-ink refresh modes exposed to the reader UI.
 *
 * Vendor-specific waveform numbers stay inside the backends.
 */
enum class EinkRefreshMode {
    /** Full-panel refresh; clears ghosting. */
    FULL,

    /** Fast/low-latency refresh. */
    FAST,

    /** Let the device pick its normal refresh strategy. */
    AUTO,
}
