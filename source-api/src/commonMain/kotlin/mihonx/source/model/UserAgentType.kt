@file:Suppress("UNUSED")

package mihonx.source.model

/**
 * Type of UserAgent a source needs
 *
 * @since extensions-lib 1.6
 */
sealed interface UserAgentType {
    /**
     * Supports both Desktop or Mobile UserAgent
     *
     * @since extensions-lib 1.6
     */
    data object Universal : UserAgentType

    /**
     * Requires Desktop UserAgent
     *
     * @since extensions-lib 1.6
     */
    data object Desktop : UserAgentType

    /**
     * Requires Mobile UserAgent
     *
     * @since extensions-lib 1.6
     */
    data object Mobile : UserAgentType

    /**
     * Extension manages its own UserAgent
     *
     * @since extensions-lib 1.6
     */
    data object Managed : UserAgentType
}
