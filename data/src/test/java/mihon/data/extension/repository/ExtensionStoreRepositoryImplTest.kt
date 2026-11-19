package mihon.data.extension.repository

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings

class ExtensionStoreRepositoryImplTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: Database
    private val service = mockk<ExtensionStoreService>()
    private val store = ExtensionStore(
        indexUrl = "https://extensions.example.org/repo.json",
        name = "Example repo",
        badgeLabel = "Example",
        signingKey = "first-key",
        contact = ExtensionStore.Contact("https://extensions.example.org", null),
        isLegacy = false,
        extensionListUrl = null,
    )

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.synchronous().create(driver)
        database = DatabaseBindings.providesDatabase(driver)
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun refreshMayMoveTheIndexButKeepsThePinnedKey() = runTest {
        val repository = ExtensionStoreRepositoryImpl(backgroundScope, service, database)
        repository.upsert(store)

        val moved = store.copy(indexUrl = "https://extensions.example.org/v2/manifest")
        coEvery { service.fetch(store.indexUrl) } returns Result.success(moved)
        repository.refreshAll()
        repository.getAll() shouldBe listOf(moved)

        coEvery { service.fetch(moved.indexUrl) } returns Result.success(moved.copy(signingKey = "new-key"))
        repository.refreshAll()
        repository.getAll() shouldBe listOf(moved)
    }

    @Test
    fun resolvingAnIndexAliasCannotOverwriteAnExistingPin() = runTest {
        val repository = ExtensionStoreRepositoryImpl(backgroundScope, service, database)
        repository.upsert(store)
        val aliasUrl = "https://extensions.example.org/index.min.json"
        coEvery { service.fetch(aliasUrl) } returns Result.success(store.copy(signingKey = "new-key"))

        repository.insert(aliasUrl).isFailure shouldBe true
        repository.getAll() shouldBe listOf(store)
    }

    @Test
    fun migrationMayInitializeThePinOnce() = runTest {
        val repository = ExtensionStoreRepositoryImpl(backgroundScope, service, database)
        repository.insertFromPreference(store.indexUrl, store.name)
        coEvery { service.fetch(store.indexUrl) } returns Result.success(store)
        repository.refreshAll()

        repository.insertFromPreference(store.indexUrl, store.name)
        shouldThrow<IllegalStateException> {
            repository.upsert(store.copy(signingKey = "new-key"))
        }
        repository.getAll() shouldBe listOf(store)
    }
}
