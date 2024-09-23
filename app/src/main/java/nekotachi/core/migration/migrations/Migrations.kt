package nekotachi.core.migration.migrations

import nekotachi.core.migration.Migration

val migrations: List<Migration>
    get() = listOf(
        SetupBackupCreateMigration(),
        SetupLibraryUpdateMigration(),
        TrustExtensionRepositoryMigration(),
    )
