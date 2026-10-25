package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The installer which installs, updates and uninstalls the extensions.
 *
 * @param context The application context.
 */
@Inject
class ExtensionInstaller(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val context: Context,
    networkHelper: NetworkHelper,
) {

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeSteps = ConcurrentHashMap<Long, MutableStateFlow<InstallStep>>()

    private val httpClient: OkHttpClient by lazy { networkHelper.client }

    /**
     * Adds the given extension to the downloads queue and returns an observable containing its
     * step in the installation process.
     *
     * @param extension The listing to install, whose apk must be signed with its store's key.
     */
    fun downloadAndInstall(extension: Extension.Available): Flow<InstallStep> {
        val downloadId = extension.pkgName.hashCode().toLong()
        cancelInstall(extension.pkgName)

        val step = MutableStateFlow<InstallStep>(InstallStep.Pending)
        activeSteps[downloadId] = step

        val job = scope.launch {
            val tmpFile = File(context.cacheDir, "extension_${extension.pkgName}.apk")
            try {
                step.value = InstallStep.Downloading
                val request = Request.Builder().url(extension.apkUrl).build()
                httpClient.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Failed to download extension")
                    }
                    // Interruptible so cancelInstall stops the body read too
                    runInterruptible {
                        response.body.byteStream().use { input ->
                            tmpFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                // The store's index names the apk, but nothing ties what's served there to the store's key
                val packageInfo = ExtensionLoader.getArchivePackageInfo(context, tmpFile)
                    ?.takeIf { extension.store.signingKey in ExtensionLoader.getSignatures(it).orEmpty() }
                if (packageInfo == null) {
                    tmpFile.delete()
                    throw Exception("Extension isn't signed with the key of ${extension.store.name}")
                }

                ensureActive()
                step.value = InstallStep.Installing
                deployExtension(downloadId, tmpFile)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.INFO, e)
                step.value = InstallStep.Error
            }
        }

        activeJobs[extension.pkgName] = job

        return step.asStateFlow()
            .onCompletion {
                activeJobs.remove(extension.pkgName)
                activeSteps.remove(downloadId)
                job.cancel()
            }
    }

    /**
     * Deploys the downloaded apk into the app's private extension directory and lets the loader pick
     * it up.
     *
     * Extensions are never handed to the system installer: they only ever run inside this app, so
     * deploying one is a file copy plus a load, and switching it back off is a preference flip
     * rather than an uninstall.
     */
    private fun deployExtension(downloadId: Long, tempFile: File) {
        try {
            ExtensionLoader.installPrivateExtensionFile(context, tempFile)
            updateInstallStep(downloadId, InstallStep.Installed)
        } catch (e: Exception) {
            logcat(LogPriority.INFO, e) { "Failed to install extension privately" }
            updateInstallStep(downloadId, InstallStep.Error)
        }

        tempFile.delete()
    }

    /**
     * Cancels a queued download and its deployment.
     */
    fun cancelInstall(pkgName: String) {
        activeJobs.remove(pkgName)?.cancel()
    }

    /**
     * Starts an intent to uninstall the extension by the given package name.
     *
     * @param pkgName The package name of the extension to uninstall
     */
    fun uninstallApk(pkgName: String) {
        if (context.isPackageInstalled(pkgName)) {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            ExtensionLoader.uninstallPrivateExtension(context, pkgName)
            ExtensionInstallReceiver.notifyRemoved(context, pkgName)
        }
    }

    /**
     * Sets the step of the installation of an extension.
     *
     * @param downloadId The id of the download.
     * @param step New install step.
     */
    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        activeSteps[downloadId]?.let { it.value = step }
    }

    companion object {
        // Also used by the app update flow, which still hands an apk to the system installer
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
