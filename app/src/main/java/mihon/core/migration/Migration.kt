package mihon.core.migration

interface Migration {
    val version: Float

    suspend fun action(migrationContext: MigrationContext): Boolean

    companion object {
        const val ALWAYS = -1f

        fun of(version: Float, action: suspend (MigrationContext) -> Boolean): Migration = object : Migration {
            override val version: Float = version

            override suspend fun action(migrationContext: MigrationContext): Boolean {
                return action(migrationContext)
            }
        }
    }
}
