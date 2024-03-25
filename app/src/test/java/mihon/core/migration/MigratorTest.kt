package mihon.core.migration

import io.mockk.Called
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MigratorTest {

    @Test
    fun initialVersion() {
        val onMigrationComplete: () -> Unit = {}
        val onMigrationCompleteSpy = spyk(onMigrationComplete)
        val didMigration = Migrator.migrate(
            old = 0,
            new = 1,
            migrations = listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { false }),
            onMigrationComplete = onMigrationCompleteSpy
        )
        verify { onMigrationCompleteSpy() }
        Assertions.assertTrue(didMigration)
    }

    @Test
    fun sameVersion() {
        val onMigrationComplete: () -> Unit = {}
        val onMigrationCompleteSpy = spyk(onMigrationComplete)
        val didMigration = Migrator.migrate(
            old = 1,
            new = 1,
            migrations = listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { true }),
            onMigrationComplete = onMigrationCompleteSpy
        )
        verify { onMigrationCompleteSpy wasNot Called }
        Assertions.assertFalse(didMigration)
    }

    @Test
    fun smallMigration() {
        val onMigrationComplete: () -> Unit = {}
        val onMigrationCompleteSpy = spyk(onMigrationComplete)
        val didMigration = Migrator.migrate(
            old = 1,
            new = 2,
            migrations = listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { true }),
            onMigrationComplete = onMigrationCompleteSpy
        )
        verify { onMigrationCompleteSpy() }
        Assertions.assertTrue(didMigration)
    }

    @Test
    fun largeMigration() {
        val onMigrationComplete: () -> Unit = {}
        val onMigrationCompleteSpy = spyk(onMigrationComplete)
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
        val didMigration = Migrator.migrate(
            old = 1,
            new = 10,
            migrations = input,
            onMigrationComplete = onMigrationCompleteSpy
        )
        verify { onMigrationCompleteSpy() }
        Assertions.assertTrue(didMigration)
    }

    @Test
    fun withinRangeMigration() {
        val onMigrationComplete: () -> Unit = {}
        val onMigrationCompleteSpy = spyk(onMigrationComplete)
        val didMigration = Migrator.migrate(
            old = 1,
            new = 2,
            migrations = listOf(
                Migration.of(Migration.ALWAYS) { true },
                Migration.of(2f) { true },
                Migration.of(3f) { false }
            ),
            onMigrationComplete = onMigrationCompleteSpy
        )
        verify { onMigrationCompleteSpy() }
        Assertions.assertTrue(didMigration)
    }
}
