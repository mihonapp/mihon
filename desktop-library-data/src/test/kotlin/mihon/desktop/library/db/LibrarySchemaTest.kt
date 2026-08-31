package mihon.desktop.library.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainAll
import org.junit.jupiter.api.Test

class LibrarySchemaTest {
    @Test
    fun `schema creates every Plan 2 table`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.use {
            DesktopLibraryDatabase.Schema.create(driver)
            val names = driver.executeQuery(
                identifier = null,
                sql = "SELECT name FROM sqlite_master WHERE type='table'",
                mapper = { cursor ->
                    val result = mutableListOf<String>()
                    while (cursor.next().value) result += cursor.getString(0)!!
                    app.cash.sqldelight.db.QueryResult.Value(result)
                },
                parameters = 0,
            ).value
            names.shouldContainAll(
                "manga", "chapter", "category", "manga_category", "history", "tracking",
                "source_metadata", "preference_snapshot", "source_preference_snapshot",
                "local_manga_entry", "local_chapter_asset", "import_report", "import_report_item",
            )
        }
    }
}
