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
        val spyk = spyk(onMigrationComplete)
        val migrations = Migrator.migrate(
            old = 0,
            new = 1,
            migrations = listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { true }),
            onMigrationComplete = spyk
        )
        verify { spyk() }
        Assertions.assertEquals(1, migrations.size)
    }

    @Test
    fun sameVersion() {
        val onMigrationComplete: () -> Unit = {}
        val spyk = spyk(onMigrationComplete)
        val migrations = Migrator.migrate(
            old = 1,
            new = 1,
            migrations = listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { true }),
            onMigrationComplete = spyk
        )
        verify { spyk wasNot Called }
        Assertions.assertEquals(0, migrations.size)
    }

    @Test
    fun smallMigration() {
        val onMigrationComplete: () -> Unit = {}
        val spyk = spyk(onMigrationComplete)
        val migrations = Migrator.migrate(
            old = 1,
            new = 2,
            migrations = listOf(Migration.of(Migration.ALWAYS) { true }, Migration.of(2f) { true }),
            onMigrationComplete = spyk
        )
        verify { spyk() }
        Assertions.assertEquals(2, migrations.size)
    }

    @Test
    fun largeMigration() {
        val onMigrationComplete: () -> Unit = {}
        val spyk = spyk(onMigrationComplete)
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
        val migrations = Migrator.migrate(
            old = 1,
            new = 10,
            migrations = input,
            onMigrationComplete = spyk
        )
        verify { spyk() }
        Assertions.assertEquals(10, migrations.size)
    }

    @Test
    fun withinRangeMigration() {
        val onMigrationComplete: () -> Unit = {}
        val spyk = spyk(onMigrationComplete)
        val migrations = Migrator.migrate(
            old = 1,
            new = 2,
            migrations = listOf(
                Migration.of(Migration.ALWAYS) { true },
                Migration.of(2f) { true },
                Migration.of(3f) { true }
            ),
            onMigrationComplete = spyk
        )
        verify { spyk() }
        Assertions.assertEquals(2, migrations.size)
    }
}
