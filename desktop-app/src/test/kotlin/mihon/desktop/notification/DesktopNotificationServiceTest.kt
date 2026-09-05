package mihon.desktop.notification

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DesktopNotificationServiceTest {

    @Test
    fun `dispatches and stores in-app notifications without crashing in headless environment`() {
        val service = WindowsDesktopNotificationService()

        service.notifyDownloadComplete("One Piece", "Chapter 1000")
        service.notifyDownloadError("Bleach", "Chapter 1", "HTTP 404")
        service.notifyLibraryUpdate(5, 2)

        val notifications = service.recentNotifications.value
        notifications shouldHaveSize 3

        notifications[0].title shouldBe "Library Updated"
        notifications[0].message shouldBe "Found 5 new chapters across 2 manga"
        notifications[0].isError shouldBe false

        notifications[1].title shouldBe "Download Failed"
        notifications[1].isError shouldBe true

        notifications[2].title shouldBe "Download Complete"
        notifications[2].isError shouldBe false

        service.clearNotifications()
        service.recentNotifications.value shouldHaveSize 0
    }
}
