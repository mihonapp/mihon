package mihon.sync

import io.kotest.matchers.shouldBe
import mihon.sync.drive.DriveFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SyncRootListingTest {

    @Test
    fun `the newest copy of a document wins when two devices created it at once`() {
        val listing = SyncRootListing(
            listOf(
                DriveFile(
                    id = "older",
                    name = "categories.json",
                    version = "7",
                    modifiedTime = "2026-10-05T17:00:01.000Z",
                ),
                DriveFile(
                    id = "newer",
                    name = "categories.json",
                    version = "3",
                    modifiedTime = "2026-10-05T17:00:02.000Z",
                ),
                DriveFile(id = "sources", name = "sources.tachibk", version = "12"),
            ),
        )

        listing["categories.json"]?.id shouldBe "newer"
        listing["sources.tachibk"]?.version shouldBe "12"
        listing["extensions.json"] shouldBe null
    }
}
