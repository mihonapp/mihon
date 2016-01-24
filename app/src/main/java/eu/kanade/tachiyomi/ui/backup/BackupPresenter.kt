package eu.kanade.tachiyomi.ui.backup

import android.os.Bundle
import eu.kanade.tachiyomi.data.backup.BackupManager
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Presenter of [BackupFragment].
 */
class BackupPresenter : BasePresenter<BackupFragment>() {

    /**
     * Database.
     */
    @Inject lateinit var db: DatabaseHelper

    /**
     * Backup manager.
     */
    private lateinit var backupManager: BackupManager

    /**
     * File where the backup is saved.
     */
    var backupFile: File? = null
        private set

    /**
     * Stream to restore a backup.
     */
    private var restoreStream: InputStream? = null

    /**
     * Id of the restartable that creates a backup.
     */
    private val CREATE_BACKUP = 1

    /**
     * Id of the restartable that restores a backup.
     */
    private val RESTORE_BACKUP = 2

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        backupManager = BackupManager(db)

        startableFirst(CREATE_BACKUP,
                { getBackupObservable() },
                { view, next -> view.onBackupCompleted() },
                { view, error -> view.onBackupError(error) })

        startableFirst(RESTORE_BACKUP,
                { getRestoreObservable() },
                { view, next -> view.onRestoreCompleted() },
                { view, error -> view.onRestoreError(error) })
    }

    /**
     * Creates a backup and saves it to a file.
     *
     * @param file the path where the file will be saved.
     */
    fun createBackup(file: File) {
        if (isUnsubscribed(CREATE_BACKUP)) {
            backupFile = file
            start(CREATE_BACKUP)
        }
    }

    /**
     * Restores a backup from a stream.
     *
     * @param stream the input stream of the backup file.
     */
    fun restoreBackup(stream: InputStream) {
        if (isUnsubscribed(RESTORE_BACKUP)) {
            restoreStream = stream
            start(RESTORE_BACKUP)
        }
    }

    /**
     * Returns the observable to save a backup.
     */
    private fun getBackupObservable(): Observable<Boolean> {
        return Observable.fromCallable {
            backupManager.backupToFile(backupFile!!)
            true
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    /**
     * Returns the observable to restore a backup.
     */
    private fun getRestoreObservable(): Observable<Boolean> {
        return Observable.fromCallable {
            backupManager.restoreFromStream(restoreStream!!)
            true
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

}
