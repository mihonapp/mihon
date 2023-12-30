package tachiyomi.core.util.lang

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class BooleanDataClassExtensionsTest {

    @Test
    fun `asBooleanArray converts data class to boolean array`() {
        assertArrayEquals(booleanArrayOf(true, false), TestClass(foo = true, bar = false).asBooleanArray())
        assertArrayEquals(booleanArrayOf(false, true), TestClass(foo = false, bar = true).asBooleanArray())
    }

    @Test
    fun `asBooleanArray throws error for invalid data classes`() {
        assertThrows<ClassCastException> {
            InvalidTestClass(foo = true, bar = "").asBooleanArray()
        }
    }

    @Test
    fun `asDataClass converts from boolean array`() {
        assertEquals(booleanArrayOf(true, false).asDataClass<TestClass>(), TestClass(foo = true, bar = false))
        assertEquals(booleanArrayOf(false, true).asDataClass<TestClass>(), TestClass(foo = false, bar = true))
    }

    @Test
    fun `asDataClass throws error for invalid boolean array`() {
        assertThrows<IllegalArgumentException> {
            booleanArrayOf(true).asDataClass<TestClass>()
        }
    }

    @Test
    fun `anyEnabled returns based on if any boolean property is enabled`() {
        assertTrue(TestClass(foo = false, bar = true).anyEnabled())
        assertFalse(TestClass(foo = false, bar = false).anyEnabled())
    }

    @Test
    fun `anyEnabled throws error for invalid class`() {
        assertThrows<ClassCastException> {
            InvalidTestClass(foo = true, bar = "").anyEnabled()
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
