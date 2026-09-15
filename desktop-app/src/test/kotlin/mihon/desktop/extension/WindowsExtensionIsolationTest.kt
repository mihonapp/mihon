package mihon.desktop.extension

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mihon.extension.model.ExtensionManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataInputStream
import java.nio.file.Path
import java.util.concurrent.ExecutionException

class WindowsExtensionIsolationTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `site block metadata survives extension HTTP and process IPC`(): Unit = runBlocking {
        assumeTrue(Platform.isWindows())
        val manager = WindowsExtensionProcessManager(
            tempDir.resolve("blocked-network").toFile(),
            onBrokerHttp = {
                mihon.extension.ipc.BrokerHttpResponse(
                    403,
                    body = "blocked",
                    finalUrl = "https://blocked.example/",
                    failureKind = mihon.extension.ipc.NetworkFailureKind.SITE_BLOCKED,
                )
            },
        )
        try {
            manager.loadExtension(packageFile("blocked", RejectedNetworkSource::class.java, 904))
            val error = runCatching { manager.getPopular(904, 1) }.exceptionOrNull()
            assertTrue(error is mihon.extension.ipc.IpcException)
            assertEquals(
                mihon.extension.ipc.NetworkFailure(
                    mihon.extension.ipc.NetworkFailureKind.SITE_BLOCKED,
                    403,
                    "blocked.example",
                ),
                (error as mihon.extension.ipc.IpcException).networkFailure,
                "Unexpected source failure: $error",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `named pipe rejects mismatched peer PID and nonce`() {
        assumeTrue(Platform.isWindows())
        for (wrongPid in listOf(true, false)) {
            WindowsAuthenticatedPipe("S-1-15-2-100-200-300-400-500-600-700").use { pipe ->
                pipe.prepare()
                val releaseClient = java.util.concurrent.CompletableFuture<Unit>()
                val client = java.util.concurrent.CompletableFuture.runAsync {
                    java.io.FileInputStream(pipe.readName).use {
                        java.io.FileOutputStream(pipe.writeName).use { output ->
                            java.io.DataOutputStream(output).apply {
                                writeUTF(if (wrongPid) pipe.nonce else "incorrect-nonce")
                                flush()
                                releaseClient.get(5, java.util.concurrent.TimeUnit.SECONDS)
                            }
                        }
                    }
                }
                val error = assertThrows(ExecutionException::class.java) {
                    pipe.authenticate(ProcessHandle.current().pid() + if (wrongPid) 1 else 0)
                }
                releaseClient.complete(Unit)
                assertTrue(
                    error.cause?.message.orEmpty().contains(if (wrongPid) "peer PID" else "authentication failed"),
                )
                client.get(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    @Test
    fun `native job memory quota rejects direct allocation above limit`() {
        assumeTrue(Platform.isWindows())
        val cp = listOf(SandboxAccessProbe::class.java, kotlin.Unit::class.java).map {
            java.io.File(it.protectionDomain.codeSource.location.toURI()).absolutePath
        }.joinToString(java.io.File.pathSeparator)
        val launcher = WindowsAppContainerLauncher(tempDir.resolve("memory-host").toFile(), 256L * 1024 * 1024)
        val process = launcher.launch(
            listOf(
                "${System.getProperty("java.home")}/bin/java.exe",
                "-Xmx32m",
                "-XX:MaxDirectMemorySize=1g",
                "-XX:+UseSerialGC",
                "-cp",
                cp,
                SandboxAccessProbe::class.java.name,
                "--memory-probe",
            ),
        )
        try {
            assertEquals("memory=true", DataInputStream(process.inputStream).readUTF())
        } finally {
            launcher.close()
        }
    }

    @Test
    fun `named pipe wiring accepts authenticated local peer`() {
        assumeTrue(Platform.isWindows())
        WindowsAuthenticatedPipe("S-1-15-2-100-200-300-400-500-600-700").use { pipe ->
            pipe.prepare()
            val client = java.util.concurrent.CompletableFuture.runAsync {
                java.io.FileInputStream(pipe.readName).use { input ->
                    java.io.FileOutputStream(pipe.writeName).use { output ->
                        java.io.DataOutputStream(output).apply {
                            writeUTF(pipe.nonce)
                            flush()
                        }
                        assertEquals(pipe.nonce, DataInputStream(input).readUTF())
                    }
                }
            }
            try {
                pipe.authenticate(ProcessHandle.current().pid())
            } catch (error: Exception) {
                if (client.isDone) client.get()
                throw error
            }
            client.get(5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    @Test
    fun `different extension packages have distinct processes and cannot overwrite sibling data`() = runBlocking {
        assumeTrue(Platform.isWindows())
        val manager = WindowsExtensionProcessManager(tempDir.resolve("two-hosts").toFile())
        try {
            manager.loadExtension(packageFile("one", IsolationSourceOne::class.java, 901))
            manager.loadExtension(packageFile("two", IsolationSourceTwo::class.java, 902))
            assertEquals("901", manager.getPopular(901, 1).mangas.single().title)
            assertEquals("902", manager.getPopular(902, 1).mangas.single().title)
            val packages = WindowsExtensionProcessManager::class.java
                .getDeclaredField("packageHosts")
                .apply { isAccessible = true }
                .get(manager) as Map<*, *>
            val second = packages["test.two"] as WindowsExtensionProcessManager
            val otherFile = second.workingDirectory.resolve("sandbox-data/own.txt")
            assertEquals("denied", manager.searchManga(901, 1, otherFile.absolutePath).mangas.single().title)
            assertEquals("902", otherFile.readText())
            manager.unloadExtension("test.one")
            assertEquals(listOf(902L), manager.getSources().map { it.id })
        } finally {
            manager.close()
        }
    }

    private fun packageFile(name: String, type: Class<*>, id: Long): java.io.File {
        // Keep fixture classes out of the parent's test classpath so the real extension loader runs.
        val classes = listOf(type, IsolationTestSource::class.java)
        val names = classes.associate { it.name.replace('.', '/') to "fixture/$name/${it.simpleName}" }
        val remapper = org.objectweb.asm.commons.SimpleRemapper(names)
        val manifest = ExtensionManifest(
            "test.$name",
            name,
            "1.0",
            1,
            1.0,
            "en",
            sources = listOf(
                mihon.extension.model.SourceDescriptor(id, name, "en", "fixture.$name.${type.simpleName}"),
            ),
        )
        val jar = java.io.ByteArrayOutputStream()
        java.util.jar.JarOutputStream(jar).use { output ->
            classes.forEach { clazz ->
                val path = clazz.name.replace('.', '/') + ".class"
                val writer = org.objectweb.asm.ClassWriter(0)
                clazz.classLoader.getResourceAsStream(path)!!.use {
                    org.objectweb.asm.ClassReader(
                        it,
                    ).accept(org.objectweb.asm.commons.ClassRemapper(writer, remapper), 0)
                }
                output.putNextEntry(java.util.jar.JarEntry("fixture/$name/${clazz.simpleName}.class"))
                output.write(writer.toByteArray())
                output.closeEntry()
            }
        }
        return tempDir.resolve("$name.mext").toFile().also { file ->
            java.util.zip.ZipOutputStream(file.outputStream()).use { output ->
                output.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                val manifestJson = Json.encodeToString(
                    ExtensionManifest.serializer(),
                    manifest,
                )
                output.write(manifestJson.toByteArray())
                output.closeEntry()
                output.putNextEntry(java.util.zip.ZipEntry("classes.jar"))
                output.write(jar.toByteArray())
                output.closeEntry()
            }
        }
    }

    @Test
    fun `detached network callbacks use authenticated package identity`() = runBlocking {
        assumeTrue(Platform.isWindows())
        val network = DesktopNetworkHelper()
        val manager = WindowsExtensionProcessManager(
            tempDir.resolve("detached-network").toFile(),
            networkHelper = network,
            onBrokerHttp = { request ->
                assertEquals("test.detached", request.extensionId)
                mihon.extension.ipc.BrokerHttpResponse(200, body = "bound")
            },
        )
        try {
            manager.loadExtension(packageFile("detached", DetachedNetworkSource::class.java, 903))
            assertEquals("bound", manager.getPopular(903, 1).mangas.single().title)
            val forged = runCatching { manager.searchManga(903, 1, "forge") }.exceptionOrNull()
            assertTrue(forged?.message.orEmpty().contains("Unregistered extension network identity"))
        } finally {
            manager.close()
            network.close()
        }
    }

    @Test
    fun `reloading an existing host repairs missing source routes`() = runBlocking {
        assumeTrue(Platform.isWindows())
        val manager = WindowsExtensionProcessManager(tempDir.resolve("repair-routes").toFile())
        try {
            val file = packageFile("one", IsolationSourceOne::class.java, 901)
            manager.loadExtension(file)
            val routes = WindowsExtensionProcessManager::class.java.getDeclaredField("sourceHosts")
                .apply { isAccessible = true }.get(manager) as MutableMap<*, *>
            routes.clear()
            manager.loadExtension(file)
            assertEquals("901", manager.getPopular(901, 1).mangas.single().title)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `sandbox blocks sibling files and direct network and job close kills child`() {
        assumeTrue(Platform.isWindows())
        val secret = tempDir.resolve("private.txt").toFile().apply { writeText("secret") }
        val forbidden = tempDir.resolve("forbidden.txt").toFile()
        val home = tempDir.resolve("isolated").toFile()
        val cp = listOf(SandboxAccessProbe::class.java, kotlin.Unit::class.java).map {
            java.io.File(it.protectionDomain.codeSource.location.toURI()).absolutePath
        }.joinToString(java.io.File.pathSeparator)
        java.net.ServerSocket(0).use { server ->
            java.net.Socket("127.0.0.1", server.localPort).close()
            val launcher = WindowsAppContainerLauncher(home, 256L * 1024 * 1024)
            val process = launcher.launch(
                listOf(
                    "${System.getProperty("java.home")}/bin/java.exe", "-Xmx32m", "-XX:+UseSerialGC", "-cp", cp,
                    SandboxAccessProbe::class.java.name,
                    secret.absolutePath,
                    forbidden.absolutePath,
                    server.localPort.toString(),
                ),
            )
            try {
                assertEquals(
                    "read=true;write=true;network=true",
                    DataInputStream(process.inputStream).readUTF(),
                )
                assertTrue(home.resolve("allowed.txt").isFile)
                assertTrue(!forbidden.exists())
            } finally {
                launcher.close()
            }
            assertTrue(!ProcessHandle.of(process.pid()).map { it.isAlive }.orElse(false))
        }
    }

    @Test
    fun `real extension host runs with an AppContainer token`() = runBlocking {
        assumeTrue(Platform.isWindows())
        WindowsExtensionProcessManager(tempDir.resolve("host 中文").toFile()).use { manager ->
            manager.start()
            assertTrue(manager.ping())
            val field = WindowsExtensionProcessManager::class.java.getDeclaredField("process")
                .apply { isAccessible = true }
            val process = field.get(manager) as Process
            val handle = Kernel32.INSTANCE.OpenProcess(0x1000, false, process.pid().toInt())
            try {
                assertTrue(WindowsSandboxNative.tokenIsAppContainer(handle))
            } finally {
                Kernel32.INSTANCE.CloseHandle(handle)
            }
        }
    }
}
