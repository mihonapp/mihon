package mihon.core.migration

class MigrationStrategyFactory(
    private val factory: MigrationJobFactory,
    private val migrationCompletedListener: MigrationCompletedListener,
) {

    fun create(old: Int, new: Int): MigrationStrategy {
        val strategy = when {
            old == 0 -> InitialMigrationStrategy(
                strategy = DefaultMigrationStrategy(factory, migrationCompletedListener, Migrator.scope),
            )
            old >= new -> NoopMigrationStrategy(false)
            else -> VersionRangeMigrationStrategy(
                versions = (old + 1)..new,
                strategy = DefaultMigrationStrategy(factory, migrationCompletedListener, Migrator.scope),
            )
        }
        return strategy
    }
}
