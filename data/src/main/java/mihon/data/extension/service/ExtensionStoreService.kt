package mihon.data.extension.service

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.model.NetworkExtensionStore
import mihon.data.extension.model.NetworkLegacyExtension
import mihon.data.extension.model.NetworkLegacyExtensionRepo
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

class ExtensionStoreService(
    private val network: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    suspend fun fetch(indexUrl: String): Result<ExtensionStore> {
        return fetch(indexUrl, forceV2 = false)
    }

    private suspend fun fetch(indexUrl: String, forceV2: Boolean): Result<ExtensionStore> {
        var updatedIndexUrl: String = indexUrl
        return try {
            val store = network.client.newCall(GET(indexUrl)).awaitSuccess().body.source().use { source ->
                try {
                    protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.peek().readByteArray())
                } catch (e: IllegalArgumentException) {
                    logcat(LogPriority.ERROR, e) {
                        "Failed to add extension store '$updatedIndexUrl'"
                    }
                    try {
                        json.decodeFromBufferedSource<NetworkExtensionStore>(source.peek())
                    } catch (e: IllegalArgumentException) {
                        if (forceV2) throw e
                        logcat(LogPriority.ERROR, e) {
                            "Failed to add extension store '$updatedIndexUrl'"
                        }
                        val legacyIndex = try {
                            json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
                        } catch (e: IllegalArgumentException) {
                            if (!indexUrl.endsWith("/index.min.json")) {
                                throw e
                            }
                            logcat(LogPriority.ERROR, e) {
                                "Failed to add extension store '$updatedIndexUrl'"
                            }
                            updatedIndexUrl = indexUrl.replace("/index.min.json", "/repo.json")
                            network.client.newCall(GET(updatedIndexUrl)).awaitSuccess().body.source().use {
                                json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it)
                            }
                        }

                        if (legacyIndex.indexV2 != null) {
                            return fetch(legacyIndex.indexV2, forceV2 = true)
                        } else {
                            legacyIndex
                        }
                    }
                }
                    .toExtensionStore(updatedIndexUrl)
            }
            Result.success(store)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Failed to add extension store '$updatedIndexUrl'"
            }
            Result.failure(e)
        }
    }

    suspend fun getExtensions(store: ExtensionStore): Result<List<Extension.Available>> {
        return try {
            val extensions = if (!store.isLegacy) {
                val response = network.client.newCall(GET(store.indexUrl)).awaitSuccess()
                response.body.source().use { source ->
                    try {
                        protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.peek().readByteArray())
                            .toAvailableExtensions(store)
                    } catch (_: IllegalArgumentException) {
                        json.decodeFromBufferedSource<NetworkExtensionStore>(source.peek())
                            .toAvailableExtensions(store)
                    }
                }
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val response = network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess()
                response.body.source().use { source ->
                    json.decodeFromBufferedSource<List<NetworkLegacyExtension>>(source)
                        .map { it.toAvailableExtension(store, storeBaseUrl) }
                }
            }
            Result.success(extensions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
