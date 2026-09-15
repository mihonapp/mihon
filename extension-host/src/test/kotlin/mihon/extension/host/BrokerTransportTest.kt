package mihon.extension.host

import mihon.extension.ipc.BrokerHttpResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class BrokerTransportTest {
    @Test fun `derived client preserves both interceptor lists and binary post`() {
        val events = mutableListOf<String>()
        val bridge = BrokerTransport("pkg.a", 42, { request ->
            events += "broker"
            assertEquals("pkg.a", request.extensionId)
            assertEquals(42L, request.sourceId)
            val duplicateHeaders = request.headerValues.entries.firstOrNull { it.key.equals("X-Duplicate", true) }
            assertEquals(listOf("one", "two"), duplicateHeaders?.value)
            assertArrayEquals(byteArrayOf(0, -1, 1), Base64.getDecoder().decode(request.bodyBase64))
            BrokerHttpResponse(
                200,
                bodyBase64 = Base64.getEncoder().encodeToString(byteArrayOf(-1, 0)),
                headerValues = mapOf("X-Reply" to listOf("a", "b")),
                finalUrl = "https://redirect.invalid/image",
            )
        })
        val client = OkHttpClient.Builder().addInterceptor(bridge).build().newBuilder()
            .addInterceptor { chain ->
                events += "application"
                chain.proceed(
                    chain.request().newBuilder().addHeader(
                        "X-Duplicate",
                        "one",
                    ).addHeader("X-Duplicate", "two").build(),
                )
            }
            .addNetworkInterceptor { chain ->
                events += "network"
                chain.withDns(okhttp3.Dns { error("Host DNS must not run") })
                    .withReadTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .proceed(chain.request())
            }
            .build()
        client.newCall(
            Request.Builder().url("https://never-connect.invalid/").post(byteArrayOf(0, -1, 1).toRequestBody()).build(),
        ).execute().use {
            assertArrayEquals(byteArrayOf(-1, 0), it.body.bytes())
            assertEquals(listOf("a", "b"), it.headers.values("X-Reply"))
            assertEquals("https://redirect.invalid/image", it.request.url.toString())
        }
        assertEquals(listOf("application", "network", "broker"), events)
    }

    @Test fun `cancel interrupts suspended broker callback`() {
        val started = java.util.concurrent.CountDownLatch(1)
        val cancelled = java.util.concurrent.CountDownLatch(1)
        val bridge = BrokerTransport("pkg", 1, {
            started.countDown()
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                cancelled.countDown()
            }
        })
        val call = OkHttpClient.Builder().addInterceptor(
            bridge,
        ).build().newCall(Request.Builder().url("https://never-connect.invalid").build())
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val task = pool.submit<Boolean> {
                try {
                    call.execute().close()
                    false
                } catch (
                    _: java.io.IOException,
                ) {
                    true
                }
            }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            call.cancel()
            assertTrue(task.get(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(cancelled.await(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun `file payload validates name reads binary and removes file`() {
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try {
            val name = "broker-${java.util.UUID.randomUUID()}.bin"
            val file = java.io.File(directory, name)
            file.writeBytes(byteArrayOf(0, -1))
            val result = BrokeredHttpClient.materialize(BrokerHttpResponse(200, bodyFileName = name), directory)
            assertArrayEquals(byteArrayOf(0, -1), Base64.getDecoder().decode(result.bodyBase64))
            assertFalse(file.exists())
            assertThrows(mihon.extension.ipc.IpcException::class.java) {
                BrokeredHttpClient.materialize(BrokerHttpResponse(200, bodyFileName = "../secret"), directory)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `image transforming interceptor sees binary and close reaches body`() {
        var closed = false
        val bridge =
            BrokerTransport("pkg", 1, {
                BrokerHttpResponse(200, bodyBase64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2)))
            })
        val client = OkHttpClient.Builder().addInterceptor(bridge).addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val transformed = response.body.bytes().reversedArray()
            response.newBuilder().body(object : okhttp3.ResponseBody() {
                override fun contentType(): okhttp3.MediaType? = null
                override fun contentLength() = transformed.size.toLong()
                private val data = okio.Buffer().write(transformed)
                override fun source(): okio.BufferedSource = data
                override fun close() {
                    closed = true
                    data.close()
                }
            }).build()
        }.build()
        client.newCall(Request.Builder().url("https://never-connect.invalid").build()).execute().use {
            assertArrayEquals(byteArrayOf(2, 1), it.body.bytes())
        }
        assertTrue(closed)
    }

    @Test fun `shared network helper keeps package and source clients distinct`() {
        eu.kanade.tachiyomi.network.NetworkHelper.installBroker(BrokeredHttpClient { null })
        val helper = eu.kanade.tachiyomi.network.NetworkHelper()
        fun get(
            packageId: String,
            sourceId: Long,
        ) = ExtensionExecutionContext.duringConstruction(
            ExtensionExecutionContext.Identity(packageId, sourceId, android.app.Application()),
        ) {
            helper.client
        }
        assertSame(get("a", 1), get("a", 1))
        assertNotSame(get("a", 1), get("b", 1))
        assertNotSame(get("a", 1), get("a", 2))
    }
}
