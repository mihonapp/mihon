package mihon.core.migration

import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MigratorTest {

    lateinit var migrationCompletedListener: MigrationCompletedListener
    lateinit var migrationContext: MigrationContext
    lateinit var migrationJobFactory: MigrationJobFactory
    lateinit var migrationStrategyFactory: MigrationStrategyFactory

    @BeforeEach
    fun initilize() {
        migrationContext = MigrationContext(false)
        migrationJobFactory = spyk(MigrationJobFactory(migrationContext, CoroutineScope(Dispatchers.Main + Job())))
        migrationCompletedListener = spyk<MigrationCompletedListener>(block = {})
        migrationStrategyFactory = spyk(MigrationStrategyFactory(migrationJobFactory, migrationCompletedListener))
    }

    @Test
    fun initialVersion() = runBlocking {
        val strategy = migrationStrategyFactory.create(0, 1)
        assertInstanceOf(InitialMigrationStrategy::class.java, strategy)

        val migrations = slot<List<Migration>>()
        val execute = strategy(listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { false }))

        execute.await()

        verify { migrationJobFactory.create(capture(migrations)) }
        assertEquals(1, migrations.captured.size)
        verify { migrationCompletedListener() }
    }

    @Test
    fun sameVersion() = runBlocking {
        val strategy = migrationStrategyFactory.create(1, 1)
        assertInstanceOf(NoopMigrationStrategy::class.java, strategy)

        val execute = strategy(listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { false }))

        val result = execute.await()
        assertFalse(result)

        verify(exactly = 0) { migrationJobFactory.create(any()) }
    }

    @Test
    fun noMigrations() = runBlocking {
        val strategy = migrationStrategyFactory.create(1, 2)
        assertInstanceOf(VersionRangeMigrationStrategy::class.java, strategy)

        val execute = strategy(emptyList())

        val result = execute.await()
        assertFalse(result)

        verify(exactly = 0) { migrationJobFactory.create(any()) }
    }

    @Test
    fun smallMigration() = runBlocking {
        val strategy = migrationStrategyFactory.create(1, 2)
        assertInstanceOf(VersionRangeMigrationStrategy::class.java, strategy)

        val migrations = slot<List<Migration>>()
        val execute = strategy(listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { true }))

        execute.await()

        verify { migrationJobFactory.create(capture(migrations)) }
        assertEquals(2, migrations.captured.size)
        verify { migrationCompletedListener() }
    }

    @Test
    fun largeMigration() = runBlocking {
        val input = listOf(
            Migration.of(Migration.ALWAYS) { true },
            Migration.of(2f) { true },
            Migration.of(3f) { true },
            Migration.of(4f) { true },
            Migration.of(5f) { true },
            Migration.of(6f) { true },
            Migration.of(7f) { true },
            Migration.of(8f) { true },
            Migration.of(9f) { true },
            Migration.of(10f) { true },
        )

        val strategy = migrationStrategyFactory.create(1, 10)
        assertInstanceOf(VersionRangeMigrationStrategy::class.java, strategy)

        val migrations = slot<List<Migration>>()
        val execute = strategy(input)

        execute.await()

        verify { migrationJobFactory.create(capture(migrations)) }
        assertEquals(10, migrations.captured.size)
        verify { migrationCompletedListener() }
    }

    @Test
    fun withinRangeMigration() = runBlocking {
        val strategy = migrationStrategyFactory.create(1, 2)
        assertInstanceOf(VersionRangeMigrationStrategy::class.java, strategy)

        val migrations = slot<List<Migration>>()
        val execute = strategy(
            listOf(
                Migration.of(Migration.ALWAYS) { true },
                Migration.of(2f) { true },
                Migration.of(3f) { false },
            ),
        )

        execute.await()

        verify { migrationJobFactory.create(capture(migrations)) }
        assertEquals(2, migrations.captured.size)
        verify { migrationCompletedListener() }
    }

    companion object {

        val mainThreadSurrogate = newSingleThreadContext("UI thread")

        @BeforeAll
        @JvmStatic
        fun setUp() {
            Dispatchers.setMain(mainThreadSurrogate)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            Dispatchers.resetMain() // reset the main dispatcher to the original Main dispatcher
            mainThreadSurrogate.close()
        }
    }
}
