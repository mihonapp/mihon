package mihon.desktop.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

data class DesktopNotificationItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val message: String,
    val isError: Boolean = false,
    val progress: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

interface DesktopNotificationService {
    val recentNotifications: StateFlow<List<DesktopNotificationItem>>

    fun notifyDownloadComplete(mangaTitle: String, chapterName: String)
    fun notifyDownloadError(mangaTitle: String, chapterName: String, error: String)
    fun notifyDownloadProgress(mangaTitle: String, chapterName: String, progress: Float)
    fun notifyLibraryUpdate(newChaptersCount: Int, mangaCount: Int)
    fun notifyExtensionUpdatePending(extensionCount: Int)
    fun clearNotifications()
}

class WindowsDesktopNotificationService(
    private val enabledProvider: () -> Boolean = { true },
    private val hideContentProvider: () -> Boolean = { false },
) : DesktopNotificationService {

    private val _recentNotifications = MutableStateFlow<List<DesktopNotificationItem>>(emptyList())
    override val recentNotifications: StateFlow<List<DesktopNotificationItem>> = _recentNotifications.asStateFlow()

    private var trayIcon: TrayIcon? = null

    init {
        initTray()
    }

    private fun initTray() {
        if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
            try {
                val tray = SystemTray.getSystemTray()
                val image: Image = runCatching {
                    WindowsDesktopNotificationService::class.java.getResourceAsStream("/icon.png")?.use {
                        javax.imageio.ImageIO.read(it)
                    }
                }.getOrNull() ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                val icon = TrayIcon(image, "Mihon")
                icon.isImageAutoSize = true
                tray.add(icon)
                trayIcon = icon
            } catch (_: Exception) {
                // Ignore if unable to add system tray icon (e.g. in certain restricted headless CI environments)
            }
        }
    }

    override fun notifyDownloadComplete(mangaTitle: String, chapterName: String) {
        val title = "Download Complete"
        val message = if (hideContentProvider()) "A chapter download completed" else "$mangaTitle - $chapterName"
        dispatch(title, message, isError = false)
    }

    override fun notifyDownloadError(mangaTitle: String, chapterName: String, error: String) {
        val title = "Download Failed"
        val message = if (hideContentProvider()) "A chapter download failed" else "$mangaTitle - $chapterName: $error"
        dispatch(title, message, isError = true)
    }

    override fun notifyDownloadProgress(mangaTitle: String, chapterName: String, progress: Float) {
        val boundedProgress = progress.coerceIn(0f, 1f)
        val percentage = (boundedProgress * 100).toInt()
        val message = if (hideContentProvider()) {
            "Downloading chapter: $percentage%"
        } else {
            "$mangaTitle - $chapterName: $percentage%"
        }
        dispatch("Download Progress", message, isError = false, progress = boundedProgress)
    }

    override fun notifyLibraryUpdate(newChaptersCount: Int, mangaCount: Int) {
        val title = "Library Updated"
        val message = "Found $newChaptersCount new chapters across $mangaCount manga"
        dispatch(title, message, isError = false)
    }

    override fun notifyExtensionUpdatePending(extensionCount: Int) {
        dispatch(
            title = "Extension Updates Available",
            message = "$extensionCount extension update(s) are ready",
            isError = false,
        )
    }

    override fun clearNotifications() {
        _recentNotifications.value = emptyList()
    }

    private fun dispatch(title: String, message: String, isError: Boolean, progress: Float? = null) {
        if (!enabledProvider()) return
        _recentNotifications.update { current ->
            (
                listOf(
                    DesktopNotificationItem(
                        title = title,
                        message = message,
                        isError = isError,
                        progress = progress,
                    ),
                ) + current
                )
                .take(50) // keep last 50
        }

        trayIcon?.let { icon ->
            try {
                val msgType = if (isError) TrayIcon.MessageType.ERROR else TrayIcon.MessageType.INFO
                icon.displayMessage(title, message, msgType)
            } catch (_: Exception) {
                // Ignore tray display failure
            }
        }
    }
}
