package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.host.BrokeredHttpClient
import mihon.extension.host.ExtensionHostEngine
import mihon.extension.ipc.GetPagePayload
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcRequest
import mihon.extension.validator.ExtensionPackageValidator
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.io.path.createTempDirectory

class NetworkHelperCompatibilityTest {

    @Test
    fun `default client contains the legacy interceptors required by Android extensions`() {
        val interceptors = NetworkHelper.createDefaultClient().interceptors

        (interceptors.firstOrNull() is UncaughtExceptionInterceptor) shouldBe true
        interceptors.any { it is UserAgentInterceptor } shouldBe true
        interceptors.any { it is CloudflareInterceptor } shouldBe true
    }

    @Test
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun `host bootstraps Json for extensions that resolve it through Injekt`() {
        ExtensionHostEngine(BrokeredHttpClient { null })

        Injekt.get<Json>().configuration.ignoreUnknownKeys shouldBe true
        Injekt.get<Json>().configuration.explicitNulls shouldBe false
        Injekt.get<kotlinx.serialization.protobuf.ProtoBuf>() shouldBe kotlinx.serialization.protobuf.ProtoBuf
    }

    @Test
    fun `host loads a supplied real extension package`() {
        val packagePath = smokePackagePath()
        assumeTrue(!packagePath.isNullOrBlank(), "No external extension package was supplied")
        val packageFile = File(requireNotNull(packagePath))
        assumeTrue(packageFile.isFile, "The supplied extension package does not exist")

        val manifest = ExtensionPackageValidator.validatePackage(packageFile)
        val sources = ExtensionHostEngine(BrokeredHttpClient { null }).loadExtension(
            packageFile = packageFile,
            workingDir = createTempDirectory("mihon-extension-smoke-").toFile(),
        )

        sources.isNotEmpty() shouldBe true
        sources.map { it.id }.toSet() shouldBe manifest.sources.map { it.id }.toSet()
    }

    @Test
    fun `host invokes a supplied real extension without missing Android clock classes`(): Unit = runBlocking {
        val packagePath = smokePackagePath()
        assumeTrue(!packagePath.isNullOrBlank(), "No external extension package was supplied")
        val packageFile = File(requireNotNull(packagePath))
        assumeTrue(packageFile.isFile, "The supplied extension package does not exist")
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        val source = engine.loadExtension(
            packageFile = packageFile,
            workingDir = createTempDirectory("mihon-extension-request-smoke-").toFile(),
        ).first()

        val response = engine.handleRequest(
            IpcRequest(
                requestId = 1L,
                command = IpcCommands.GET_POPULAR,
                payloadJson = Json.encodeToString(GetPagePayload(source.id, 1)),
            ),
        )

        (response.error?.contains("ClassNotFound") == true) shouldBe false
    }

    private fun smokePackagePath(): String? =
        System.getProperty("mihon.extension.smoke.package")
            ?: System.getenv("MIHON_EXTENSION_SMOKE_PACKAGE")
}
