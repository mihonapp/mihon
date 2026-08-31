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
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            val version = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor ->
                    cursor.next().value
                    QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
            ).value
            if (version == 0L) {
                DesktopLibraryDatabase.Schema.create(driver)
                driver.execute(
                    identifier = null,
                    sql = "PRAGMA user_version=${DesktopLibraryDatabase.Schema.version}",
                    parameters = 0,
                )
            }
            return SqlDelightLibraryRepository(driver, DesktopLibraryDatabase(driver))
        } catch (error: Throwable) {
            driver.close()
            throw error
        }
    }
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
