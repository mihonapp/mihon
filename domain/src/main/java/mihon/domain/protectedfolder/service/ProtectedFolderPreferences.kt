package mihon.domain.protectedfolder.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Every key is app state, which keeps the PIN hash and any other sign of the folder out of backups.
 */
@Inject
@SingleIn(AppScope::class)
class ProtectedFolderPreferences(
    preferenceStore: PreferenceStore,
) {

    val pinHash: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("protected_folder_pin_hash"),
        "",
    )

    val pinSalt: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("protected_folder_pin_salt"),
        "",
    )

    val failedAttempts: Preference<Int> = preferenceStore.getInt(
        Preference.appStateKey("protected_folder_failed_attempts"),
        0,
    )

    val lockedOutUntil: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("protected_folder_locked_out_until"),
        0,
    )

    /**
     * Minutes without interaction after which the folder locks.
     */
    val lockAfter: Preference<Int> = preferenceStore.getInt(
        Preference.appStateKey("protected_folder_lock_after"),
        1,
    )

    val secureScreen: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("protected_folder_secure_screen"),
        true,
    )

    val useBiometrics: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("protected_folder_use_biometrics"),
        false,
    )

    val includeInBackups: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("protected_folder_include_in_backups"),
        false,
    )
}
