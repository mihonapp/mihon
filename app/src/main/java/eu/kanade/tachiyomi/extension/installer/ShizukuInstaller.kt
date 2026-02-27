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
                getActiveEntry()?.uri?.let { uri ->
                    try {
                        service.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to delete source APK for $packageName" }
                    }
                }
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
            scope.launch { installWithRetry(entry) }
        } else {
            try {
                service.contentResolver.openAssetFileDescriptor(entry.uri, "r").use { fd ->
                    shellInterface?.install(fd)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId}" }
                continueQueue(InstallStep.Error)
            }
        }
    }

    private suspend fun installWithRetry(entry: Entry) {
        var tempFile: File? = null
        try {
            // getPackageArchiveInfo requires a file path, not a content URI
            tempFile = File(service.cacheDir, "install_${System.currentTimeMillis()}.apk")
            service.contentResolver.openInputStream(entry.uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw Exception("Failed to open APK input stream")

            val apkInfo = service.packageManager.getPackageArchiveInfo(
                tempFile.absolutePath,
                PackageManager.GET_SIGNATURES,
            ) ?: throw Exception("Failed to read APK package info")
            val apkPackageName = apkInfo.packageName
            val apkSignatures = apkInfo.signatures

            val installedInfo = try {
                service.packageManager.getPackageInfo(apkPackageName, PackageManager.GET_SIGNATURES)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            if (installedInfo != null) {
                val installedSignatures = installedInfo.signatures

                // Only uninstall if both signatures are present and don't match
                if (apkSignatures != null && installedSignatures != null) {
                    val signaturesMatch = apkSignatures.size == installedSignatures.size &&
                        apkSignatures.zip(installedSignatures).all { (a, b) ->
                            a.toByteArray().contentEquals(b.toByteArray())
                        }

                    if (!signaturesMatch) {
                        logcat { "Signatures differ for $apkPackageName, uninstalling existing package" }
                        withContext(Dispatchers.IO) {
                            shellInterface?.uninstall(apkPackageName)
                        }
                    }
                } else {
                    logcat(LogPriority.WARN) {
                        "Cannot verify signatures for $apkPackageName (new sig: ${apkSignatures != null}, installed sig: ${installedSignatures != null})"
                    }
                }
            }

            tempFile.delete()
            tempFile = null

            service.contentResolver.openAssetFileDescriptor(entry.uri, "r")?.use { fd ->
                shellInterface?.install(fd) ?: throw Exception("Shizuku shell interface not available")
            } ?: throw Exception("Failed to open APK file descriptor")
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed during pre-install steps for ${entry.downloadId}" }
            tempFile?.delete()
            withContext(Dispatchers.Main) { continueQueue(InstallStep.Error) }
        }
    }

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
