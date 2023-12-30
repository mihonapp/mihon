package tachiyomi.core.util.lang

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class BooleanArrayExtensionsTest {

    @Test
    fun `converts to boolean array`() {
        assertArrayEquals(booleanArrayOf(true, false), TestClass(foo = true, bar = false).asBooleanArray())
        assertArrayEquals(booleanArrayOf(false, true), TestClass(foo = false, bar = true).asBooleanArray())
    }

    @Test
    fun `throws error for invalid data classes`() {
        assertThrows<ClassCastException> {
            InvalidTestClass(foo = true, bar = "").asBooleanArray()
        }
    }

    @Test
    fun `converts from boolean array`() {
        assertEquals(booleanArrayOf(true, false).asDataClass<TestClass>(), TestClass(foo = true, bar = false))
        assertEquals(booleanArrayOf(false, true).asDataClass<TestClass>(), TestClass(foo = false, bar = true))
    }

    @Test
    fun `throws error for invalid boolean array`() {
        assertThrows<IllegalArgumentException> {
            booleanArrayOf(true).asDataClass<TestClass>()
        }
    }

    data class TestClass(
        val foo: Boolean,
        val bar: Boolean,
    )

    data class InvalidTestClass(
        val foo: Boolean,
        val bar: String,
    )
}
