package eu.kanade.tachiyomi.extension.installer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.IBinder
import androidx.core.content.ContextCompat
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.shizuku.IShellInterface
import mihon.app.shizuku.ShellInterface
import rikka.shizuku.Shizuku
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class ShizukuInstaller(private val service: Service) : Installer(service) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = Injekt.get<BasePreferences>()
    private val reinstallOnFailure get() = preferences.shizukuReinstallOnFailure().get()

    private var shellInterface: IShellInterface? = null

    private val shizukuArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(service, ShellInterface::class.java),
        )
            .tag("shizuku_service")
            .processNameSuffix("shizuku_service")
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellInterface = IShellInterface.Stub.asInterface(service)
            ready = true
            checkQueue()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellInterface = null
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

            if (status == PackageInstaller.STATUS_SUCCESS) {
                continueQueue(InstallStep.Installed)
            } else {
                logcat(LogPriority.ERROR) { "Failed to install extension $packageName: $message" }
                continueQueue(InstallStep.Error)
            }
        }
    }

    private val shizukuDeadListener = Shizuku.OnBinderDeadListener {
        logcat { "Shizuku was killed prematurely" }
        service.stopSelf()
    }

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    checkQueue()
                    Shizuku.bindUserService(shizukuArgs, connection)
                } else {
                    service.stopSelf()
                }
                Shizuku.removeRequestPermissionResultListener(this)
            }
        }
    }

    fun initShizuku() {
        if (ready) return
        if (!Shizuku.pingBinder()) {
            logcat(LogPriority.ERROR) { "Shizuku is not ready to use" }
            service.toast(MR.strings.ext_installer_shizuku_stopped)
            service.stopSelf()
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Shizuku.bindUserService(shizukuArgs, connection)
        } else {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    override var ready = false

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        if (reinstallOnFailure) {
            // Use reinstall logic if enabled
            scope.launch {
                installWithRetry(entry)
            }
        } else {
            // Use normal installation
            try {
                service.contentResolver.openAssetFileDescriptor(entry.uri, "r").use { fd ->
                    shellInterface?.install(fd)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId} ${entry.uri}" }
                continueQueue(InstallStep.Error)
            }
        }
    }

    private suspend fun installWithRetry(entry: Entry) {
        var tempFile: File? = null
        try {
            // Copy APK to a temporary file for signature extraction
            tempFile = File(service.cacheDir, "install_${System.currentTimeMillis()}.apk")
            service.contentResolver.openInputStream(entry.uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Failed to open APK input stream")

            // Extract package info and signatures from the APK
            val apkInfo = service.packageManager.getPackageArchiveInfo(
                tempFile.absolutePath,
                PackageManager.GET_SIGNATURES,
            )
            if (apkInfo == null) {
                throw Exception("Failed to read APK package info")
            }
            val apkPackageName = apkInfo.packageName
            val apkSignatures = apkInfo.signatures

            // Check if the package is already installed
            val installedInfo = try {
                service.packageManager.getPackageInfo(apkPackageName, PackageManager.GET_SIGNATURES)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            // Compare signatures if installed
            if (installedInfo != null) {
                val installedSignatures = installedInfo.signatures

                // Handle null signatures safely
                if (apkSignatures != null && installedSignatures != null) {
                    val signaturesMatch = apkSignatures.size == installedSignatures.size &&
                        apkSignatures.zip(installedSignatures).all { (a, b) ->
                            a.toByteArray().contentEquals(b.toByteArray())
                        }

                    if (!signaturesMatch) {
                        logcat { "Signatures differ for $apkPackageName, uninstalling existing package" }
                        val uninstallSuccess = uninstallPackage(apkPackageName)
                        if (!uninstallSuccess) {
                            throw Exception("Failed to uninstall $apkPackageName")
                        }
                    }
                } else {
                    // If either signatures array is null, treat as mismatch and uninstall
                    logcat { "Signatures are null for $apkPackageName, uninstalling existing package" }
                    val uninstallSuccess = uninstallPackage(apkPackageName)
                    if (!uninstallSuccess) {
                        throw Exception("Failed to uninstall $apkPackageName")
                    }
                }
            }

            // Clean up temp file before installation
            tempFile.delete()
            tempFile = null

            // Proceed with installation using the original content URI
            service.contentResolver.openAssetFileDescriptor(entry.uri, "r")?.use { fd ->
                shellInterface?.install(fd) ?: throw Exception("Shizuku shell interface not available")
            } ?: throw Exception("Failed to open APK file descriptor")

            // Installation initiated successfully; queue will continue via broadcast receiver
            // Do NOT call continueQueue here - it will be called by the broadcast receiver
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed during pre-install steps for ${entry.downloadId}" }
            // Clean up temp file if it exists
            tempFile?.delete()
            // Only continue queue with error if we haven't initiated installation
            // The broadcast receiver will handle success/error for initiated installations
            withContext(Dispatchers.Main) {
                continueQueue(InstallStep.Error)
            }
        }
    }

    private suspend fun uninstallPackage(packageName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = shellInterface?.runCommand("pm uninstall $packageName")
                result?.contains("Success") == true
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to uninstall $packageName" }
                false
            }
        }
    }

    // Don't cancel if entry is already started installing
    override fun cancelEntry(entry: Entry): Boolean = getActiveEntry() != entry

    override fun onDestroy() {
        Shizuku.removeBinderDeadListener(shizukuDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        if (Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(shizukuArgs, connection, true)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to unbind shizuku service" }
            }
        }
        service.unregisterReceiver(receiver)
        logcat { "ShizukuInstaller destroy" }
        scope.cancel()
        super.onDestroy()
    }

    init {
        Shizuku.addBinderDeadListener(shizukuDeadListener)

        ContextCompat.registerReceiver(
            service,
            receiver,
            IntentFilter(ACTION_INSTALL_RESULT),
            ContextCompat.RECEIVER_EXPORTED,
        )

        initShizuku()
    }
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 14045
const val ACTION_INSTALL_RESULT = "${BuildConfig.APPLICATION_ID}.ACTION_INSTALL_RESULT"

