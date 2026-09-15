package mihon.desktop.extension

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.validator.ExtensionPackageValidator
import mihon.reader.image.ImageFormatDetector
import mihon.reader.image.ReaderImageFormat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class WindowsExtensionProcessManagerTest {

    @Test
    fun `installed host resolves a real chapter and downloads its first image`(
        @TempDir tempDir: Path,
    ): Unit = runBlocking {
        val executable = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
        val extension = System.getenv("MIHON_EXTENSION_SMOKE_PACKAGE")?.let(::File)
        assumeTrue(executable?.isFile == true, "No packaged Mihon executable was supplied")
        assumeTrue(extension?.isFile == true, "No external extension package was supplied")
        val packageFile = requireNotNull(extension)
        val manifest = ExtensionPackageValidator.validatePackage(packageFile)
        val manager = WindowsExtensionProcessManager(
            workingDirectory = tempDir.resolve("real-page-download").toFile(),
            customCommand = listOf(requireNotNull(executable).absolutePath, "--extension-host", "--stdio"),
        )
        try {
            val sources = manager.loadExtension(packageFile)
            val source = sources.firstOrNull { it.lang == "all" } ?: sources.first()
            val manga = manager.getPopular(source.id, 1).mangas.first()
            val details = manager.getMangaDetails(source.id, manga)
            val chapter = manager.getChapterList(source.id, details).first()
            val page = manager.getPageList(source.id, chapter).first()
            val imageUrl = page.imageUrl ?: page.url
            val networkHelper = DesktopNetworkHelper()
            networkHelper.registerExtensionDomains(manifest.id, manifest.declaredDomains)
            networkHelper.registerRuntimePageUrl(imageUrl)
            val bytes = networkHelper.downloadRawBytes(
                BrokerHttpRequest(
                    method = "GET",
                    url = imageUrl,
                    headers = mapOf("Referer" to chapter.url) + page.headers,
                ),
            )
            (ImageFormatDetector.detect(bytes) != ReaderImageFormat.UNKNOWN) shouldBe true
        } finally {
            manager.close()
        }
    }

    @Test
    fun `installed host registers and invokes every source from all supplied packages`(@TempDir tempDir: Path): Unit =
        runBlocking {
            val executable = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
            val extensionDirectory = System.getenv("MIHON_EXTENSION_SMOKE_DIRECTORY")?.let(::File)
            assumeTrue(executable?.isFile == true, "No packaged Mihon executable was supplied")
            assumeTrue(extensionDirectory?.isDirectory == true, "No extension package directory was supplied")
            val packages = requireNotNull(extensionDirectory).walkTopDown()
                .filter { it.isFile && it.extension.equals("mext", ignoreCase = true) }
                .toList()
            assumeTrue(packages.isNotEmpty(), "The supplied directory contains no extension packages")

            val manager = WindowsExtensionProcessManager(
                workingDirectory = tempDir.resolve("all-installed-extensions").toFile(),
                customCommand = listOf(requireNotNull(executable).absolutePath, "--extension-host", "--stdio"),
            )
            try {
                val expectedIds = mutableSetOf<Long>()
                packages.forEach { packageFile ->
                    val manifestIds = ExtensionPackageValidator.validatePackage(packageFile).sources.map {
                        it.id
                    }.toSet()
                    manager.loadExtension(packageFile).map { it.id }.toSet() shouldBe manifestIds
                    expectedIds += manifestIds
                }
                manager.getSources().map { it.id }.toSet() shouldBe expectedIds
                val compatibilityFailures = expectedIds.mapNotNull { sourceId ->
                    val error = runCatching { manager.getPopular(sourceId, 1) }.exceptionOrNull()?.message.orEmpty()
                    if (error.isNotBlank()) println("SOURCE_REQUEST_ERROR $sourceId $error")
                    val isCompatibilityFailure = listOf(
                        "ClassNotFound",
                        "NoClassDefFound",
                        "NoSuchMethod",
                        "Source with ID",
                        "does not implement",
                    ).any { marker -> error.contains(marker, ignoreCase = true) }
                    if (isCompatibilityFailure) "$sourceId: $error" else null
                }
                compatibilityFailures.shouldBeEmpty()
            } finally {
                manager.close()
            }
        }

    @Test
    fun `source manager reloads extension after packaged host crashes`(@TempDir tempDir: Path): Unit = runBlocking {
        val executable = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
        val extension = System.getenv("MIHON_EXTENSION_SMOKE_PACKAGE")?.let(::File)
        assumeTrue(executable?.isFile == true, "No packaged Mihon executable was supplied")
        assumeTrue(extension?.isFile == true, "No external extension package was supplied")

        val preferences = DesktopPreferenceStore(tempDir.resolve("preferences.properties"))
        val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), preferences)
        val installed = installer.installFromLocalFile(requireNotNull(extension), trustOnInstall = true)
        val manager = WindowsExtensionProcessManager(
            workingDirectory = tempDir.resolve("crash-reload-host").toFile(),
            customCommand = listOf(requireNotNull(executable).absolutePath, "--extension-host", "--stdio"),
        )
        val sourceManager = DesktopSourceManager(installer, manager, preferences)
        val sourceId = installed.manifest.sources.last().id
        try {
            manager.start()
            sourceManager.ensureSourceLoaded(sourceId)
            manager.getSources().any { it.id == sourceId } shouldBe true

            val processField = WindowsExtensionProcessManager::class.java.getDeclaredField("process")
            processField.isAccessible = true
            val process = processField.get(manager) as Process
            process.destroyForcibly()
            process.waitFor()
            while (manager.state != HostProcessState.CRASHED) yield()

            sourceManager.ensureSourceLoaded(sourceId)
            manager.getSources().any { it.id == sourceId } shouldBe true
        } finally {
            sourceManager.close()
        }
    }

    @Test
    fun `installed host loads and invokes a real extension package`(@TempDir tempDir: Path): Unit = runBlocking {
        val executable = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
        val extension = System.getenv("MIHON_EXTENSION_SMOKE_PACKAGE")?.let(::File)
        assumeTrue(executable?.isFile == true, "No packaged Mihon executable was supplied")
        assumeTrue(extension?.isFile == true, "No external extension package was supplied")
        val manager = WindowsExtensionProcessManager(
            workingDirectory = tempDir.resolve("installed-real-extension").toFile(),
            customCommand = listOf(requireNotNull(executable).absolutePath, "--extension-host", "--stdio"),
        )
        try {
            val packageFile = requireNotNull(extension)
            val expectedSourceIds = ExtensionPackageValidator.validatePackage(packageFile).sources.map { it.id }.toSet()
            val sources = manager.loadExtension(packageFile)
            sources.map { it.id }.toSet() shouldBe expectedSourceIds
            sources.forEach { source ->
                manager.getPopular(source.id, 1)
            }
        } finally {
            manager.close()
        }
    }

    @Test
    fun `source manager concurrently invokes every language from a real source factory`(
        @TempDir tempDir: Path,
    ): Unit = runBlocking {
        val executable = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
        val extension = System.getenv("MIHON_EXTENSION_SMOKE_PACKAGE")?.let(::File)
        assumeTrue(executable?.isFile == true, "No packaged Mihon executable was supplied")
        assumeTrue(extension?.isFile == true, "No external extension package was supplied")

        val preferences = DesktopPreferenceStore(tempDir.resolve("preferences.properties"))
        val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), preferences)
        val installed = installer.installFromLocalFile(requireNotNull(extension), trustOnInstall = true)
        installed.manifest.sources.map { it.id }.toSet() shouldBe setOf(
            1759845183972082995L,
            5939253174175011564L,
            560822675588930101L,
            2384499981488817067L,
        )
        val processManager = WindowsExtensionProcessManager(
            workingDirectory = tempDir.resolve("concurrent-language-host").toFile(),
            customCommand = listOf(requireNotNull(executable).absolutePath, "--extension-host", "--stdio"),
        )
        val sourceManager = DesktopSourceManager(installer, processManager, preferences)
        try {
            repeat(5) {
                coroutineScope {
                    installed.manifest.sources.flatMap { source ->
                        listOf(
                            async { sourceManager.getPopular(source.id, 1) },
                            async { sourceManager.loadFilterList(source.id) },
                        )
                    }.awaitAll()
                }
            }
        } finally {
            sourceManager.close()
        }
    }

    @Test
    fun `queries wait for an in-flight host start instead of observing STARTING`(@TempDir tempDir: Path) {
        runBlocking {
            val manager = WindowsExtensionProcessManager(
                workingDirectory = tempDir.resolve("ext-concurrent-start").toFile(),
            )
            try {
                val start = async { manager.start() }
                while (manager.state == HostProcessState.STOPPED) yield()

                manager.getSources().shouldBeEmpty()
                start.await()
                manager.state shouldBe HostProcessState.RUNNING
            } finally {
                manager.close()
            }
        }
    }

    @Test
    fun `process manager launches host, pings, handles queries, and shuts down`(@TempDir tempDir: Path) {
        runBlocking {
            val workingDir = tempDir.resolve("ext-work").toFile()
            val manager = WindowsExtensionProcessManager(workingDirectory = workingDir)
            try {
                manager.start()
                manager.state shouldBe HostProcessState.RUNNING

                val isAlive = manager.ping()
                isAlive shouldBe true

                val sources = manager.getSources()
                sources.shouldBeEmpty()

                // Test restart
                manager.restart()
                manager.state shouldBe HostProcessState.RUNNING
                manager.ping() shouldBe true
            } finally {
                manager.close()
                manager.state shouldBe HostProcessState.STOPPED
            }
        }
    }

    @Test
    fun `process manager traps crash and marks state as crashed`(@TempDir tempDir: Path) {
        runBlocking {
            val workingDir = tempDir.resolve("ext-crash").toFile()
            val manager = WindowsExtensionProcessManager(workingDirectory = workingDir)
            try {
                manager.start()
                manager.state shouldBe HostProcessState.RUNNING

                // Force kill host process
                val procField = WindowsExtensionProcessManager::class.java.getDeclaredField("process")
                procField.isAccessible = true
                val proc = procField.get(manager) as Process
                proc.destroyForcibly()
                proc.waitFor()

                delay(100)
                manager.state shouldBe HostProcessState.CRASHED

                // After crash, ensureRunningSession should auto-restart
                val sources = manager.getSources()
                sources.shouldBeEmpty()
                manager.state shouldBe HostProcessState.RUNNING
            } finally {
                manager.close()
            }
        }
    }
}
