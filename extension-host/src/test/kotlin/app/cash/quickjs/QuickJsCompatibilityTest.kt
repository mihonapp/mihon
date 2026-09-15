package app.cash.quickjs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class QuickJsCompatibilityTest {
    @Test
    fun `runtime isolates sessions rejects Java and terminates loops`() {
        QuickJs.create().use { runtime ->
            assertEquals("undefined", runtime.evaluate("typeof Packages"))
            runtime.evaluate("var privateValue = 7")
            QuickJs.create().use { other -> assertEquals("undefined", other.evaluate("typeof privateValue")) }
            assertThrows(IllegalStateException::class.java) { runtime.evaluate("while (true) {}") }
            assertThrows(UnsupportedOperationException::class.java) { runtime.evaluate("({a:1})") }
        }
        val closed = QuickJs.create()
        closed.close()
        assertThrows(IllegalStateException::class.java) { closed.evaluate("1") }
    }

    @Test
    fun `extension evaluate ABI returns extracted JSON strings`() {
        val type = Class.forName("app.cash.quickjs.QuickJs")
        val runtime = type.getMethod("create").invoke(null)
        try {
            assertEquals(
                "[\"https://img.example/1.jpg\"]",
                type.getMethod(
                    "evaluate",
                    String::class.java,
                ).invoke(runtime, "JSON.stringify(['https://img.example/1.jpg'])"),
            )
        } finally {
            type.getMethod("close").invoke(runtime)
        }
    }
}
