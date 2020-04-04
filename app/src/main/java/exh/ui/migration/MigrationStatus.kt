package exh.ui.migration

class MigrationStatus {
    companion object {
        val NOT_INITIALIZED = -1
        val COMPLETED = 0

        // Migration process
        val NOTIFY_USER = 1
        val OPEN_BACKUP_MENU = 2
        val PERFORM_BACKUP = 3
        val FINALIZE_MIGRATION = 4

        val MAX_MIGRATION_STEPS = 2
    }
}
