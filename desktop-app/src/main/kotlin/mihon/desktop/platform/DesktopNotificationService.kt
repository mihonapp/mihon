package mihon.desktop.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.TrayIcon.MessageType
import java.awt.image.BufferedImage

enum class NotificationType {
    INFO,
    WARNING,
    ERROR,
}

data class DesktopNotificationEvent(
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val timestamp: Long = System.currentTimeMillis(),
)

class DesktopNotificationService(
    private val enabledProvider: () -> Boolean = { true },
    private val hideContentProvider: () -> Boolean = { false },
) {
    private val _notifications = MutableStateFlow<DesktopNotificationEvent?>(null)
    val notifications: StateFlow<DesktopNotificationEvent?> = _notifications.asStateFlow()

    private var trayIcon: TrayIcon? = null

    init {
        initTray()
    }

    private fun initTray() {
        try {
            if (SystemTray.isSupported()) {
                val tray = SystemTray.getSystemTray()
                val image: Image = runCatching {
                    DesktopNotificationService::class.java.getResourceAsStream("/icon.png")?.use {
                        javax.imageio.ImageIO.read(it)
                    }
                }.getOrNull() ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                val icon = TrayIcon(image, "Mihon")
                icon.isImageAutoSize = true
                tray.add(icon)
                trayIcon = icon
            }
        } catch (_: Throwable) {
            trayIcon = null
        }
    }

    fun notify(
        title: String,
        message: String,
        type: NotificationType = NotificationType.INFO,
    ) {
        if (!enabledProvider()) return

        val visibleMessage = if (hideContentProvider()) "Open Mihon W to view details" else message
        val event = DesktopNotificationEvent(title = title, message = visibleMessage, type = type)
        _notifications.value = event

        val icon = trayIcon
        if (icon != null) {
            try {
                val messageType = when (type) {
                    NotificationType.INFO -> MessageType.INFO
                    NotificationType.WARNING -> MessageType.WARNING
                    NotificationType.ERROR -> MessageType.ERROR
                }
                icon.displayMessage(title, visibleMessage, messageType)
            } catch (_: Throwable) {
                // Ignore tray display error
            }
        }
    }

    fun clearLastNotification() {
        _notifications.value = null
    }
}
