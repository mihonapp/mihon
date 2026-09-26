package mihon.core.navigation.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class SettingsDestination(val id: Int) {
    @Serializable
    data object About : SettingsDestination(0)

    @Serializable
    data object DataAndStorage : SettingsDestination(1)

    @Serializable
    data object Tracking : SettingsDestination(2)
}
