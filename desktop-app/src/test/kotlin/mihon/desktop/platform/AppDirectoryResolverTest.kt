package mihon.desktop.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AppDirectoryResolverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `installed mode roots state in APPDATA`() {
        val appData = tempDir.resolve("Roaming")
        val resolver = AppDirectoryResolver(appData, tempDir.resolve("bin"))

        resolver.resolve(DistributionMode.Installed).root shouldBe appData.resolve("MihonW")
    }

    @Test
    fun `portable mode roots state beside executable`() {
        val executableDir = tempDir.resolve("MihonW")
        val resolver = AppDirectoryResolver(tempDir.resolve("Roaming"), executableDir)

        resolver.resolve(DistributionMode.Portable).root shouldBe executableDir.resolve("data")
    }

    @Test
    fun `explicit data root wins in either mode`() {
        val explicit = tempDir.resolve("chosen")
        val resolver = AppDirectoryResolver(tempDir.resolve("Roaming"), tempDir.resolve("bin"))

        resolver.resolve(DistributionMode.Portable, explicit).root shouldBe explicit
    }

    @Test
    fun `create builds every required directory`() {
        val directories = AppDirectoryResolver(tempDir.resolve("Roaming"), tempDir.resolve("bin"))
            .resolve(DistributionMode.Installed)
            .create()

        listOf(
            directories.root,
            directories.cache,
            directories.logs,
            directories.extensions,
            directories.database,
        ).all(Files::isDirectory) shouldBe true
    }
}
