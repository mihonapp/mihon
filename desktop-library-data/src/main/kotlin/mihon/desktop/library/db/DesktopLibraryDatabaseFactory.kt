package mihon.desktop.library.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DesktopLibraryDatabaseFactory {
    private const val ENABLE_FOREIGN_KEYS = "PRAGMA foreign_keys=ON"
    private const val USER_VERSION_QUERY = "PRAGMA user_version"

    fun open(path: Path): SqlDelightLibraryRepository = open(path, null)

    /** Internal statement instrumentation used only by deterministic driver tests. */
    internal fun open(
        path: Path,
        afterStatement: ((String) -> Unit)?,
    ): SqlDelightLibraryRepository {
        val absolutePath = path.toAbsolutePath()
        Files.createDirectories(absolutePath.parent)
        val driver = SingleConnectionSqliteDriver("jdbc:sqlite:$absolutePath", afterStatement)
        try {
            driver.execute(null, ENABLE_FOREIGN_KEYS, 0)
            val database = DesktopLibraryDatabase(driver)
            val supportedVersion = DesktopLibraryDatabase.Schema.version
            val currentVersion = readUserVersion(driver)
                ?: throw DesktopLibraryDatabaseOpenException.MissingVersion(absolutePath)

            when {
                currentVersion < 0L -> throw DesktopLibraryDatabaseOpenException.NegativeVersion(
                    path = absolutePath,
                    version = currentVersion,
                )

                currentVersion > supportedVersion -> throw DesktopLibraryDatabaseOpenException.UnsupportedVersion(
                    path = absolutePath,
                    version = currentVersion,
                    supportedVersion = supportedVersion,
                )

                currentVersion == 0L -> createDatabase(database, driver, absolutePath, supportedVersion)

                currentVersion < supportedVersion -> migrateDatabase(
                    database = database,
                    driver = driver,
                    path = absolutePath,
                    oldVersion = currentVersion,
                    newVersion = supportedVersion,
                )
            }
            return SqlDelightLibraryRepository(driver, database)
        } catch (error: Throwable) {
            try {
                driver.close()
            } catch (closeError: Throwable) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }

    private fun readUserVersion(driver: SingleConnectionSqliteDriver): Long? =
        driver.executeQuery(
            identifier = null,
            sql = USER_VERSION_QUERY,
            mapper = { cursor ->
                val hasRow = cursor.next().value
                QueryResult.Value(if (hasRow) cursor.getLong(0) else null)
            },
            parameters = 0,
        ).value

    private fun hasSchemaObjects(driver: SingleConnectionSqliteDriver): Boolean =
        driver.executeQuery(
            identifier = null,
            sql = """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type IN ('table', 'index', 'view', 'trigger')
                  AND name NOT LIKE 'sqlite_%'
            """.trimIndent(),
            mapper = { cursor ->
                cursor.next().value
                QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L)
            },
            parameters = 0,
        ).value

    private fun createDatabase(
        database: DesktopLibraryDatabase,
        driver: SingleConnectionSqliteDriver,
        path: Path,
        version: Long,
    ) {
        if (hasSchemaObjects(driver)) {
            throw DesktopLibraryDatabaseOpenException.MissingVersion(path)
        }
        try {
            database.transaction {
                DesktopLibraryDatabase.Schema.create(driver)
                writeUserVersion(driver, version)
            }
        } catch (error: Throwable) {
            throw DesktopLibraryDatabaseOpenException.CreationFailed(path, error)
        }
    }

    private fun migrateDatabase(
        database: DesktopLibraryDatabase,
        driver: SingleConnectionSqliteDriver,
        path: Path,
        oldVersion: Long,
        newVersion: Long,
    ) {
        try {
            database.transaction {
                DesktopLibraryDatabase.Schema.migrate(driver, oldVersion, newVersion)
                writeUserVersion(driver, newVersion)
            }
        } catch (error: Throwable) {
            throw DesktopLibraryDatabaseOpenException.MigrationFailed(path, oldVersion, newVersion, error)
        }
    }

    private fun writeUserVersion(driver: SingleConnectionSqliteDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }
}

/**
 * Typed failures raised while opening the desktop library database.
 *
 * The factory refuses to recreate or silently downgrade a database it does not understand.
 */
sealed class DesktopLibraryDatabaseOpenException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause) {
    class MissingVersion(path: Path) : DesktopLibraryDatabaseOpenException(
        "Desktop library database $path is missing a valid user_version; refusing to recreate it.",
    )

    class NegativeVersion(
        path: Path,
        val version: Long,
    ) : DesktopLibraryDatabaseOpenException(
        "Desktop library database $path has invalid negative user_version $version.",
    )

    class UnsupportedVersion(
        path: Path,
        val version: Long,
        val supportedVersion: Long,
    ) : DesktopLibraryDatabaseOpenException(
        "Desktop library database $path has user_version $version, but this build supports at most " +
            "$supportedVersion; refusing to open a newer database.",
    )

    class MigrationFailed(
        path: Path,
        val fromVersion: Long,
        val toVersion: Long,
        cause: Throwable,
    ) : DesktopLibraryDatabaseOpenException(
        "Failed to migrate desktop library database $path from version $fromVersion to $toVersion.",
        cause,
    )

    class CreationFailed(
        path: Path,
        cause: Throwable,
    ) : DesktopLibraryDatabaseOpenException(
        "Failed to create desktop library database $path at the current schema version.",
        cause,
    )
}

private class SingleConnectionSqliteDriver(
    url: String,
    private val afterStatement: ((String) -> Unit)?,
) : JdbcDriver() {
    private val connection = DriverManager.getConnection(url)
    private val listeners = mutableMapOf<String, MutableSet<Query.Listener>>()
    private val connectionLock = ReentrantLock(true)

    override fun getConnection(): Connection {
        connectionLock.lock()
        return connection
    }

    override fun closeConnection(connection: Connection) {
        connectionLock.unlock()
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> =
        super.execute(identifier, sql, parameters, binders).also { afterStatement?.invoke(sql) }

    override fun Connection.beginTransaction() {
        try {
            check(autoCommit) { "Expected autoCommit to be true before starting a transaction" }
            autoCommit = false
        } catch (error: Throwable) {
            closeConnection(this)
            throw error
        }
    }

    override fun Connection.endTransaction() {
        try {
            commit()
            autoCommit = true
        } finally {
            closeConnection(this)
        }
    }

    override fun Connection.rollbackTransaction() {
        try {
            rollback()
            autoCommit = true
        } finally {
            closeConnection(this)
        }
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) {
            queryKeys.forEach { queryKey -> listeners.getOrPut(queryKey, ::mutableSetOf).add(listener) }
        }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) {
            queryKeys.forEach { queryKey -> listeners[queryKey]?.remove(listener) }
        }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val changed = synchronized(listeners) {
            queryKeys.flatMap { queryKey -> listeners[queryKey].orEmpty() }.toSet()
        }
        changed.forEach(Query.Listener::queryResultsChanged)
    }

    override fun close() {
        connectionLock.withLock { connection.close() }
    }
}
