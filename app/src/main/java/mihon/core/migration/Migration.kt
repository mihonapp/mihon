package mihon.core.migration

interface Migration {
    val version: Float

    suspend operator fun invoke(migrationContext: MigrationContext): Boolean

    val isAlways: Boolean
        get() = version == ALWAYS

    companion object {
        const val ALWAYS = -1f

        fun of(version: Float, action: suspend (MigrationContext) -> Boolean): Migration = object : Migration {
            override val version: Float = version

            override suspend operator fun invoke(migrationContext: MigrationContext): Boolean {
                return action(migrationContext)
            }
        }
    }
}
