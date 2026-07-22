package mihon.core.common

import kotlin.properties.Delegates
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object FeatureFlags {

    var verboseLoggingDefault by Delegates.notNull<Boolean>()
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun newInstallationId(): String {
        return Uuid.random().toHexDashString()
    }

    fun init(
        verboseLoggingDefault: Boolean,
    ) {
        this.verboseLoggingDefault = verboseLoggingDefault
    }
}
