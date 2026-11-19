package mihon.data.extension.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ExtensionStoreRepositoryImpl(
    @AppCoroutineScope scope: CoroutineScope,
    private val service: ExtensionStoreService,
    private val database: Database,
) : ExtensionStoreRepository {

    private val stores = database.extension_storeQueries.getAll(::extensionStoreMapper)
        .subscribeToList()
        .catch {
            logcat(LogPriority.ERROR, it) { "Failed to query extension stores" }
            emit(emptyList())
        }
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(
                stopTimeout = 5.seconds,
                replayExpiration = Duration.ZERO,
            ),
            replay = 1,
        )

    override suspend fun insert(indexUrl: String): Result<Unit> {
        return service.fetch(indexUrl).mapCatching { saveStore(it, indexUrl) }
    }

    override suspend fun insertFromPreference(indexUrl: String, name: String) {
        database.transaction {
            if (getSigningKey(indexUrl) != null) return@transaction

            database.extension_storeQueries.upsert(
                indexUrl = indexUrl,
                name = name,
                badgeLabel = name,
                signingKey = UNVERIFIED_SIGNING_KEY,
                contactWebsite = indexUrl,
                contactDiscord = null,
                isLegacy = false,
                extensionListUrl = null,
            )
        }
    }

    override suspend fun refreshAll() {
        try {
            val refreshed = getAll().mapNotNull { store ->
                service.fetch(store.indexUrl)
                    .onFailure {
                        logcat(LogPriority.ERROR, it) {
                            "Failed to refresh extension store '${store.name} (${store.indexUrl})'"
                        }
                    }
                    .getOrNull()
                    ?.let { store.indexUrl to it }
            }
            refreshed.forEach { (indexUrl, store) ->
                try {
                    saveStore(store, indexUrl, isRefresh = true)
                } catch (_: SigningKeyChangedException) {
                    logcat(LogPriority.WARN) { "Ignored signing key change for extension store '${store.name}'" }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun upsert(store: ExtensionStore) = saveStore(store)

    private suspend fun saveStore(
        store: ExtensionStore,
        indexUrl: String = store.indexUrl,
        isRefresh: Boolean = false,
    ) = database.transaction {
        val currentKey = getSigningKey(indexUrl)
        if (isRefresh && currentKey == null) return@transaction

        checkSigningKey(currentKey, store.signingKey)
        if (indexUrl != store.indexUrl) {
            checkSigningKey(getSigningKey(store.indexUrl), store.signingKey)
        }

        database.extension_storeQueries.upsert(
            indexUrl = store.indexUrl,
            name = store.name,
            badgeLabel = store.badgeLabel,
            signingKey = store.signingKey,
            contactWebsite = store.contact.website,
            contactDiscord = store.contact.discord,
            isLegacy = store.isLegacy,
            extensionListUrl = store.extensionListUrl,
        )
        if (isRefresh && indexUrl != store.indexUrl) {
            database.extension_storeQueries.delete(indexUrl)
        }
    }

    private suspend fun getSigningKey(indexUrl: String): String? =
        database.extension_storeQueries.getSigningKey(indexUrl).awaitAsOneOrNull()

    private fun checkSigningKey(currentKey: String?, signingKey: String) {
        if (currentKey != null && currentKey != UNVERIFIED_SIGNING_KEY && currentKey != signingKey) {
            throw SigningKeyChangedException()
        }
    }

    private class SigningKeyChangedException : IllegalStateException(
        "The repository signing key changed. Verify the new key before removing and adding the repository again.",
    )

    override suspend fun fetchExtensions(): List<Extension.Available> {
        return try {
            supervisorScope {
                database.extension_storeQueries.getAll(::extensionStoreMapper).awaitAsList().map { store ->
                    async {
                        service.getExtensions(store).onFailure {
                            this@ExtensionStoreRepositoryImpl.logcat(LogPriority.ERROR, it) {
                                "Failed to fetch extensions for store '${store.name} (${store.indexUrl})'"
                            }
                        }
                    }
                }
                    .awaitAll()
                    .flatMap { it.getOrDefault(emptyList()) }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun getAll(): List<ExtensionStore> {
        return database.extension_storeQueries.getAll(::extensionStoreMapper).awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<ExtensionStore>> {
        return stores
    }

    override fun getCountAsFlow(): Flow<Long> {
        return database.extension_storeQueries
            .getCount()
            .subscribeToOne()
    }

    override suspend fun remove(indexUrl: String) {
        database.extension_storeQueries.delete(indexUrl)
    }

    private fun extensionStoreMapper(
        indexUrl: String,
        name: String,
        badgeLabel: String,
        signingKey: String,
        contactWebsite: String,
        contactDiscord: String?,
        isLegacy: Boolean,
        extensionListUrl: String?,
    ): ExtensionStore = ExtensionStore(
        indexUrl = indexUrl,
        name = name,
        badgeLabel = badgeLabel,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(
            website = contactWebsite,
            discord = contactDiscord,
        ),
        isLegacy = isLegacy,
        extensionListUrl = extensionListUrl,
    )

    private companion object {
        const val UNVERIFIED_SIGNING_KEY = "NO_SIGNING_KEY"
    }
}
