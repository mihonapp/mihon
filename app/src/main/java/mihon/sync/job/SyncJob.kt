package mihon.sync.job

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreateWorker
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreWorker
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import mihon.sync.SyncManager
import mihon.sync.SyncPreferences
import mihon.sync.SyncScheduler
import mihon.sync.auth.SyncAuthRequiredException
import mihon.sync.drive.DriveStorageFullException
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.util.concurrent.TimeUnit

class SyncJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var syncManager: SyncManager

    @Inject private lateinit var syncPreferences: SyncPreferences

    @Inject private lateinit var notifier: BackupNotifier

    @Inject private lateinit var syncScheduler: SyncScheduler

    override suspend fun doWork(): Result {
        graph.inject(this)

        if (!syncPreferences.isEnabled().get()) {
            syncScheduler.onJobStopped()
            return Result.success()
        }

        // Without any account there is nothing to do, and nothing to report: only a grant that was lost
        // needs the user. Restoring a backup turns the sync on before an account is linked, and the round
        // the app starts on coming back from the sign-in page runs before the link is finished.
        if (syncPreferences.refreshToken().get().isBlank() && syncPreferences.accountEmail().get().isBlank()) {
            syncScheduler.onJobStopped()
            return Result.success()
        }

        // The backup jobs rewrite the same tables; let whichever started first finish.
        if (BackupCreateWorker.isManualJobRunning(context) || BackupRestoreWorker.isRunning(context.workManager)) {
            syncScheduler.onJobStopped()
            return Result.retry()
        }

        setForegroundSafely()

        return try {
            // Finished as far as the user is concerned once the library is in step: the history
            // written after that is bookkeeping nobody needs to wait for.
            syncManager.sync(onSettled = { syncScheduler.onJobFinished(success = true) })
            Result.success()
        } catch (e: CancellationException) {
            // Stopped from the notification, or by WorkManager when the network went away. Not worth
            // flagging as a failure: the next round simply does the work again.
            syncScheduler.onJobStopped()
            throw e
        } catch (e: SyncAuthRequiredException) {
            // Retrying cannot fix a revoked or missing grant, so stop and ask the user instead.
            logcat(LogPriority.WARN, e) { "Sync needs the Google account to be linked again" }
            syncScheduler.onJobFinished(success = false)
            notifier.showSyncError(context.stringResource(MR.strings.sync_error_auth))
            Result.failure()
        } catch (e: DriveStorageFullException) {
            // Retrying cannot free space, so say what is wrong instead of failing again and again.
            logcat(LogPriority.WARN, e) { "Sync stopped: the Google Drive is full" }
            syncScheduler.onJobFinished(success = false)
            notifier.showSyncError(context.stringResource(MR.strings.sync_error_drive_full))
            Result.failure()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Sync failed" }
            syncScheduler.onJobFinished(success = false)
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                notifier.showSyncError(e.message ?: context.stringResource(MR.strings.sync_error))
                Result.failure()
            }
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.syncProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        private val MIN_LIFECYCLE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30)

        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_AUTO) || context.workManager.isRunning(TAG_MANUAL)
        }

        /**
         * (Re)schedules the periodic sync. Called on app start and whenever the interval changes.
         */
        fun setupTask(context: Context, prefInterval: Int? = null) {
            val syncPreferences = context.appGraph.syncPreferences
            val interval = prefInterval ?: syncPreferences.syncInterval().get()

            if (interval <= 0 || !syncPreferences.isEnabled().get()) {
                context.workManager.cancelUniqueWork(TAG_AUTO)
                return
            }

            val request = PeriodicWorkRequestBuilder<SyncJob>(
                interval.toLong(),
                TimeUnit.HOURS,
                10,
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints(
                        requiredNetworkType = NetworkType.CONNECTED,
                        requiresBatteryNotLow = true,
                    ),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .addTag(TAG_AUTO)
                .build()

            context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * Entry point for the app-lifecycle triggers: opening the app pulls in what the other devices
         * did, closing it publishes what this one did.
         *
         * A round that finds nothing to do costs two listings, so the window only absorbs the same
         * device opening and closing in quick succession. It used to be five minutes, which meant
         * picking a device back up shortly after its last round showed none of what was just read
         * on the other one.
         */
        fun startIfStale(context: Context) {
            val syncPreferences = context.appGraph.syncPreferences
            if (!syncPreferences.syncOnAppLifecycle().get()) return

            val elapsed = System.currentTimeMillis() - syncPreferences.lastSyncAt().get()
            if (elapsed < MIN_LIFECYCLE_INTERVAL_MS) return

            startNow(context)
        }

        /**
         * Fired right after the user changed something worth publishing: an entry favourited or
         * removed, or a reading session ended.
         *
         * Hands over to [mihon.sync.SyncScheduler] rather than starting a round on the spot, so a
         * burst of actions collapses into one sync and the user can see — and skip — the wait.
         */
        fun onUserAction(context: Context) {
            context.appGraph.syncScheduler.schedule()
        }

        /**
         * Runs a sync now, and says whether one was actually queued: none is while sync is off.
         *
         * [visible] is for a round the user asked for, which the indicator then shows.
         *
         * [ExistingWorkPolicy.KEEP] is right for the periodic and manual paths, where a second
         * trigger adds nothing. Action triggers pass [ExistingWorkPolicy.APPEND_OR_REPLACE] instead:
         * dropping them would silently lose whatever the user did while a round was already
         * running. A redundant round is cheap — it lists one folder and finds nothing to do.
         */
        fun startNow(
            context: Context,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
            visible: Boolean = false,
        ): Boolean {
            if (!context.appGraph.syncPreferences.isEnabled().get()) return false
            if (visible) context.appGraph.syncScheduler.onSyncRequested()

            val request = OneTimeWorkRequestBuilder<SyncJob>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .addTag(TAG_MANUAL)
                .build()

            context.workManager.enqueueUniqueWork(TAG_MANUAL, policy, request)
            return true
        }

        /**
         * Stops the round in progress, whichever trigger started it. Cancelling a periodic run takes
         * its schedule with it, so the schedule is put straight back.
         */
        fun stop(context: Context) {
            val workManager = context.workManager
            val running = WorkQuery.Builder.fromTags(listOf(TAG_AUTO, TAG_MANUAL))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()

            workManager.getWorkInfos(running).get().forEach { work ->
                workManager.cancelWorkById(work.id)
                if (TAG_AUTO in work.tags) setupTask(context)
            }
        }
    }
}

private const val TAG_AUTO = "LibrarySync"
private const val TAG_MANUAL = "$TAG_AUTO:manual"
