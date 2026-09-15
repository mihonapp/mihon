package eu.kanade.tachiyomi.extension.installer

import android.app.Service
import android.net.Uri
import androidx.annotation.CallSuper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import uy.kohesive.injekt.injectLazy
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Base implementation class for extension installer. To be used inside a foreground [Service].
 */
@OptIn(ExperimentalAtomicApi::class)
abstract class Installer(private val service: Service) {

    private val extensionManager: ExtensionManager by injectLazy()

    private var waitingInstall = AtomicReference<Entry?>(null)
    private val queue = Collections.synchronizedList(mutableListOf<Entry>())

    private val cancelListener: (Long) -> Unit = { downloadId -> cancelQueue(downloadId) }

    /**
     * Installer readiness. If false, queue check will not run.
     *
     * @see checkQueue
     */
    abstract var ready: Boolean

    /**
     * Add an item to install queue.
     *
     * @param downloadId Download ID as known by [ExtensionManager]
     * @param uri Uri of APK to install
     */
    fun addToQueue(downloadId: Long, uri: Uri) {
        queue.add(Entry(downloadId, uri))
        checkQueue()
    }

    /**
     * Proceeds to install the APK of this entry inside this method. Call [continueQueue]
     * when the install process for this entry is finished to continue the queue.
     *
     * @param entry The [Entry] of item to process
     * @see continueQueue
     */
    @CallSuper
    open fun processEntry(entry: Entry) {
        extensionManager.setInstalling(entry.downloadId)
    }

    /**
     * Called before queue continues. Override this to handle when the removed entry is
     * currently being processed.
     *
     * @return true if this entry can be removed from queue.
     */
    open fun cancelEntry(entry: Entry): Boolean {
        return true
    }

    /**
     * Tells the queue to continue processing the next entry and updates the install step
     * of the completed entry ([waitingInstall]) to [ExtensionManager].
     *
     * @param resultStep new install step for the processed entry.
     * @see waitingInstall
     */
    fun continueQueue(resultStep: InstallStep) {
        val completedEntry = waitingInstall.exchange(null)
        if (completedEntry != null) {
            extensionManager.updateInstallStep(completedEntry.downloadId, resultStep)
            checkQueue()
        }
    }

    /**
     * Checks the queue. The provided service will be stopped if the queue is empty.
     * Will not be run when not ready.
     *
     * @see ready
     */
    fun checkQueue() {
        if (!ready) {
            return
        }
        if (queue.isEmpty()) {
            service.stopSelf()
            return
        }
        val nextEntry = queue.first()
        if (waitingInstall.compareAndSet(null, nextEntry)) {
            queue.removeAt(0)
            processEntry(nextEntry)
        }
    }

    /**
     * Call this method when the provided service is destroyed.
     */
    @CallSuper
    open fun onDestroy() {
        cancelListeners -= cancelListener
        queue.forEach { extensionManager.updateInstallStep(it.downloadId, InstallStep.Error) }
        queue.clear()
        waitingInstall.store(null)
    }

    protected fun getActiveEntry(): Entry? = waitingInstall.load()

    /**
     * Cancels queue for the provided download ID if exists.
     *
     * @param downloadId Download ID as known by [ExtensionManager]
     */
    private fun cancelQueue(downloadId: Long) {
        val waitingInstall = this.waitingInstall.load()
        val toCancel = queue.find { it.downloadId == downloadId } ?: waitingInstall ?: return
        if (cancelEntry(toCancel)) {
            queue.remove(toCancel)
            if (waitingInstall == toCancel) {
                // Currently processing removed entry, continue queue
                this.waitingInstall.store(null)
                checkQueue()
            }
            extensionManager.updateInstallStep(downloadId, InstallStep.Idle)
        }
    }

    /**
     * Install item to queue.
     *
     * @param downloadId Download ID as known by [ExtensionManager]
     * @param uri Uri of APK to install
     */
    data class Entry(val downloadId: Long, val uri: Uri)

    init {
        cancelListeners += cancelListener
    }

    companion object {
        private val cancelListeners = CopyOnWriteArraySet<(Long) -> Unit>()

        /**
         * Attempts to cancel the installation entry for the provided download ID.
         *
         * Runs on the calling thread, so the cancellation is done by the time this returns.
         *
         * @param downloadId Download ID as known by [ExtensionManager]
         */
        fun cancelInstallQueue(downloadId: Long) {
            cancelListeners.forEach { it(downloadId) }
        }
    }
}
