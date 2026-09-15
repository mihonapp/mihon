package mihon.extension.host

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class ExtensionClassLoaderTest {
    @Test
    fun `adapted classes preserve package bytes and release archive handles`(@TempDir directory: Path) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/TempFiles", null, "java/lang/Object", null)
        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "create", "()Ljava/io/File;", null, null).apply {
            visitCode()
            visitLdcInsn("fixture-")
            visitInsn(Opcodes.ACONST_NULL)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/io/File",
                "createTempFile",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/io/File;",
                false,
            )
            visitInsn(Opcodes.ARETURN)
            visitMaxs(2, 0)
            visitEnd()
        }
        writer.visitEnd()
        val archive = directory.resolve("extension.jar")
        JarOutputStream(Files.newOutputStream(archive)).use {
            it.putNextEntry(JarEntry("fixture/TempFiles.class"))
            it.write(writer.toByteArray())
            it.closeEntry()
        }
        val original = Files.readAllBytes(archive)
        repeat(2) {
            ExtensionClassLoader(arrayOf(archive.toUri().toURL())).use { loader ->
                val file = loader.loadClass("fixture.TempFiles").getMethod("create").invoke(null) as File
                assertTrue(file.isFile)
                Files.delete(file.toPath())
            }
            assertArrayEquals(original, Files.readAllBytes(archive))
            // Windows denies deletion if a JarURLConnection leaked its cached JarFile.
            Files.delete(archive)
            Files.write(archive, original)
        }
    }
}
