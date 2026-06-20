package tachiyomi.core.common.storage

import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class UniFileExtensionsTest {

    @Test
    fun `uses rename when supported`() {
        val source = mockFile()
        every { source.renameTo("001.jpg") } returns true

        assertSame(source, source.renameToOrCopy("001.jpg"))
        verify(exactly = 1) { source.renameTo("001.jpg") }
        verify(exactly = 0) { source.parentFile }
    }

    @Test
    fun `copies file and deletes source when rename is unsupported`() {
        val contents = "image".toByteArray()
        val output = ByteArrayOutputStream()
        val parent = mockDirectory()
        val source = mockFile()
        val target = mockFile()
        every { source.renameTo("001.jpg") } returns false
        every { source.parentFile } returns parent
        every { parent.findFile("001.jpg") } returns null
        every { parent.createFile("001.jpg") } returns target
        every { source.openInputStream() } returns ByteArrayInputStream(contents)
        every { target.openOutputStream() } returns output
        every { source.delete() } returns true

        assertSame(target, source.renameToOrCopy("001.jpg"))
        assertArrayEquals(contents, output.toByteArray())
        verify(exactly = 1) { source.delete() }
        verify(exactly = 0) { target.delete() }
    }

    @Test
    fun `copies directories recursively`() {
        val contents = "image".toByteArray()
        val output = ByteArrayOutputStream()
        val parent = mockDirectory()
        val source = mockDirectory()
        val child = mockFile()
        val target = mockDirectory()
        val childTarget = mockFile()
        every { source.renameTo("chapter") } returns false
        every { source.parentFile } returns parent
        every { parent.findFile("chapter") } returns null
        every { parent.createDirectory("chapter") } returns target
        every { source.listFiles() } returns arrayOf(child)
        every { child.name } returns "001.jpg"
        every { target.createFile("001.jpg") } returns childTarget
        every { child.openInputStream() } returns ByteArrayInputStream(contents)
        every { childTarget.openOutputStream() } returns output
        every { source.delete() } returns true

        assertSame(target, source.renameToOrCopy("chapter"))
        assertArrayEquals(contents, output.toByteArray())
    }

    @Test
    fun `fails without overwriting an existing target`() {
        val parent = mockDirectory()
        val source = mockFile(path = "/source.tmp")
        every { source.renameTo("source.jpg") } returns false
        every { source.parentFile } returns parent
        every { parent.findFile("source.jpg") } returns mockFile()

        assertThrows(IOException::class.java) {
            source.renameToOrCopy("source.jpg")
        }
        verify(exactly = 0) { parent.createFile(any()) }
    }

    @Test
    fun `removes incomplete target when copying fails`() {
        val parent = mockDirectory()
        val source = mockFile(path = "/source.tmp")
        val target = mockFile()
        every { source.renameTo("source.jpg") } returns false
        every { source.parentFile } returns parent
        every { parent.findFile("source.jpg") } returns null
        every { parent.createFile("source.jpg") } returns target
        every { source.openInputStream() } throws IOException("read failed")
        every { target.delete() } returns true

        assertThrows(IOException::class.java) {
            source.renameToOrCopy("source.jpg")
        }
        verify(exactly = 1) { target.delete() }
        verify(exactly = 0) { source.delete() }
    }

    @Test
    fun `removes copied target when deleting source fails`() {
        val parent = mockDirectory()
        val source = mockFile(path = "/source.tmp")
        val target = mockFile()
        every { source.renameTo("source.jpg") } returns false
        every { source.parentFile } returns parent
        every { parent.findFile("source.jpg") } returns null
        every { parent.createFile("source.jpg") } returns target
        every { source.openInputStream() } returns ByteArrayInputStream(byteArrayOf())
        every { target.openOutputStream() } returns ByteArrayOutputStream()
        every { source.delete() } returns false
        every { target.delete() } returns true

        assertThrows(IOException::class.java) {
            source.renameToOrCopy("source.jpg")
        }
        verify(exactly = 1) { target.delete() }
    }

    private fun mockFile(path: String = "/file"): UniFile = mockk {
        every { isDirectory } returns false
        every { filePath } returns path
    }

    private fun mockDirectory(): UniFile = mockk {
        every { isDirectory } returns true
    }
}
