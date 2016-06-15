package eu.kanade.tachiyomi.data.library

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.AppModule
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.CustomRobolectricGradleTestRunner
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowApplication
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.registry.default.DefaultRegistrar

@Config(constants = BuildConfig::class, sdk = intArrayOf(Build.VERSION_CODES.LOLLIPOP))
@RunWith(CustomRobolectricGradleTestRunner::class)
class LibraryUpdateAlarmTest {

    lateinit var app: ShadowApplication
    lateinit var context: Context
    lateinit var alarmManager: ShadowAlarmManager

    @Before
    fun setup() {
        app = ShadowApplication.getInstance()
        context = spy(app.applicationContext)
        Injekt = InjektScope(DefaultRegistrar())
        Injekt.importModule(AppModule(context as App))

        alarmManager = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    }

    @Test
    fun testLibraryIntentHandling() {
        val intent = Intent(LibraryUpdateAlarm.LIBRARY_UPDATE_ACTION)
        assertThat(app.hasReceiverForIntent(intent)).isTrue()
    }

    @Test
    fun testAlarmIsNotStarted() {
        assertThat(alarmManager.nextScheduledAlarm).isNull()
    }

    @Test
    fun testAlarmIsNotStartedWhenBootReceivedAndSettingZero() {
        val alarm = LibraryUpdateAlarm()
        alarm.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(alarmManager.nextScheduledAlarm).isNull()
    }

    @Test
    fun testAlarmIsStartedWhenBootReceivedAndSettingNotZero() {
        val prefs = PreferencesHelper(context)
        prefs.libraryUpdateInterval().set(1)

        val alarm = LibraryUpdateAlarm()
        alarm.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(alarmManager.nextScheduledAlarm).isNotNull()
    }

    @Test
    fun testOnlyOneAlarmExists() {
        val prefs = PreferencesHelper(context)
        prefs.libraryUpdateInterval().set(1)

        LibraryUpdateAlarm.startAlarm(context)
        LibraryUpdateAlarm.startAlarm(context)
        LibraryUpdateAlarm.startAlarm(context)

        assertThat(alarmManager.scheduledAlarms).hasSize(1)
    }

    @Test
    fun testLibraryWillBeUpdatedWhenAlarmFired() {
        val prefs = PreferencesHelper(context)
        prefs.libraryUpdateInterval().set(1)

        val expectedIntent = Intent(context, LibraryUpdateAlarm::class.java)
        expectedIntent.action = LibraryUpdateAlarm.LIBRARY_UPDATE_ACTION

        LibraryUpdateAlarm.startAlarm(context)

        val scheduledAlarm = alarmManager.nextScheduledAlarm
        val pendingIntent = shadowOf(scheduledAlarm.operation)
        assertThat(pendingIntent.isBroadcastIntent).isTrue()
        assertThat(pendingIntent.savedIntents).hasSize(1)
        assertThat(expectedIntent.component).isEqualTo(pendingIntent.savedIntents[0].component)
        assertThat(expectedIntent.action).isEqualTo(pendingIntent.savedIntents[0].action)
    }

    @Test
    fun testReceiverDoesntReactToNullActions() {
        val prefs = PreferencesHelper(context)
        prefs.libraryUpdateInterval().set(1)

        val intent = Intent(context, LibraryUpdateService::class.java)

        val alarm = LibraryUpdateAlarm()
        alarm.onReceive(context, Intent())

        assertThat(app.nextStartedService).isNotEqualTo(intent)
        assertThat(alarmManager.scheduledAlarms).hasSize(0)
    }

    @Test
    fun testAlarmFiresCloseToDesiredTime() {
        val hours = 2
        LibraryUpdateAlarm.startAlarm(context, hours)

        val shouldRunAt = SystemClock.elapsedRealtime() + hours * 60 * 60 * 1000

        // Margin error of 3 seconds
        assertThat(alarmManager.nextScheduledAlarm.triggerAtTime)
                .isGreaterThan(shouldRunAt - 3000)
                .isLessThan(shouldRunAt + 3000)
    }

}
