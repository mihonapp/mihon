package mihon.desktop.extension

import kotlinx.coroutines.runBlocking
import mihon.extension.ipc.BrokerHttpResponse
import mihon.extension.validator.ExtensionPackageValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class InstalledExtensionRegressionTest {
    @Test
    fun `installed source URLs and requests survive isolated routing`(@TempDir directory: Path) = runBlocking {
        val packages = System.getenv("MIHON_EXTENSION_SMOKE_DIRECTORY")?.let(::File)
        assumeTrue(packages?.isDirectory == true)
        val executable = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
        val live = System.getenv("MIHON_EXTENSION_LIVE_SMOKE") == "1"
        val failures = java.util.concurrent.CopyOnWriteArrayList<String>()
        val network = DesktopNetworkHelper()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val manager = WindowsExtensionProcessManager(
            directory.resolve("host").toFile(),
            customCommand = executable?.let { listOf(it.absolutePath, "--extension-host", "--stdio") },
            networkHelper = network,
            onBrokerHttp = { request ->
                val host = java.net.URI(request.url).host
                val allowed = network.isDomainAllowed(host, request.extensionId)
                requests.add("${request.extensionId}|$host|$allowed")
                if (!allowed) failures.add("Domain denied: ${request.extensionId} $host")
                if (live) {
                    network.executeBrokeredRequest(request)
                } else {
                    BrokerHttpResponse(418, body = "fixture reached", finalUrl = request.url)
                }
            },
        )
        try {
            requireNotNull(packages).walkTopDown().filter { it.extension == "mext" }.forEach { file ->
                val manifest = ExtensionPackageValidator.validatePackage(file)
                val sources = manager.loadExtension(file)
                sources.forEach { source ->
                    if (source.baseUrl.isNullOrBlank()) {
                        failures.add(
                            "Missing URL on load: ${source.name}/${source.lang}",
                        )
                    }
                }
                manager.getSources().filter { it.id in sources.map { s -> s.id } }.forEach { source ->
                    if (source.baseUrl.isNullOrBlank()) {
                        failures.add(
                            "Missing URL on lookup: ${source.name}/${source.lang}",
                        )
                    }
                }
                val before = requests.size
                val result = runCatching { manager.getPopular(sources.first().id, 1) }
                val error = result.exceptionOrNull()
                println(
                    "REGRESSION ${manifest.id}: ${sources.map {
                        it.baseUrl
                    }} count=${result.getOrNull()?.mangas?.size} error=${error?.message}",
                )
                if (live && error != null) failures.add("Live request failed: ${manifest.id}: ${error.message}")
                if (requests.size == before) failures.add("No broker request: ${manifest.id}: ${error?.message}")
                assertFalse(
                    network.isDomainAllowed("unrelated.invalid", manifest.id) && manifest.declaredDomains.isEmpty(),
                )
            }
            println("BROKER_REQUESTS $requests")
            assertEquals(emptyList<String>(), failures)
        } finally {
            manager.close()
            network.close()
            if (live && failures.isNotEmpty()) {
                manager.workingDirectory.walkTopDown().filter { it.name == "extension-host-stderr.log" }.forEach {
                    println("HOST_STDERR ${it.relativeTo(manager.workingDirectory)}\n${it.readText()}")
                }
            }
        }
    }
}
