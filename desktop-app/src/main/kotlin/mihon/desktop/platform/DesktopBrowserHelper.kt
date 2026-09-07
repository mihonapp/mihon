package mihon.desktop.platform

import java.awt.Desktop
import java.net.URI

object DesktopBrowserHelper {

    fun openInBrowser(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }

        // 1. Try java.awt.Desktop
        try {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(uri)
                    return true
                }
            }
        } catch (_: Exception) {
            // Fall through to OS native command
        }

        // 2. Fallback to platform-specific process launch
        val os = System.getProperty("os.name", "").lowercase()
        return try {
            when {
                os.contains("win") -> {
                    ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
                    true
                }
                os.contains("mac") -> {
                    ProcessBuilder("open", url).start()
                    true
                }
                else -> {
                    ProcessBuilder("xdg-open", url).start()
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
