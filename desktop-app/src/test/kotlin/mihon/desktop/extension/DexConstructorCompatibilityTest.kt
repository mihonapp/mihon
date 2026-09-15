package mihon.desktop.extension

import mihon.desktop.extension.compat.TachiyomiExtensionConverter
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class DexConstructorCompatibilityTest {
    @Test
    fun `normalizer restores the exact original DEX allocated type`() {
        val node = com.googlecode.d2j.node.DexFileNode()
        node.visit(1, "LActual;", "Ljava/lang/Object;", emptyArray()).visitEnd()
        val factory = node.visit(1, "LFactory;", "Ljava/lang/Object;", emptyArray())
        factory.visitMethod(
            9,
            com.googlecode.d2j.Method("LFactory;", "create", emptyArray(), "LActual;"),
        ).visitCode().apply {
            visitRegister(1)
            visitTypeStmt(com.googlecode.d2j.reader.Op.NEW_INSTANCE, 0, -1, "LActual;")
            visitMethodStmt(
                com.googlecode.d2j.reader.Op.INVOKE_DIRECT,
                intArrayOf(0),
                com.googlecode.d2j.Method("Ljava/lang/Object;", "<init>", emptyArray(), "V"),
            )
            visitStmt1R(com.googlecode.d2j.reader.Op.RETURN_OBJECT, 0)
            visitEnd()
        }
        mihon.desktop.extension.compat.DexConstructorNormalizer.normalize(node)
        val restored = node.clzs.first().methods.single()
        org.junit.jupiter.api.Assertions.assertEquals("LActual;", restored.method.owner)
        val call = node.clzs.last().methods.single().codeNode.stmts[1] as com.googlecode.d2j.node.insn.MethodStmtNode
        org.junit.jupiter.api.Assertions.assertEquals("LActual;", call.method.owner)
        val superCall = restored.codeNode.stmts.first() as com.googlecode.d2j.node.insn.MethodStmtNode
        org.junit.jupiter.api.Assertions.assertEquals("Ljava/lang/Object;", superCall.method.owner)
    }

    @Test
    fun `invalid R8 constructor lowering is rejected before package publication`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "Broken", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_STATIC, "instance", "LBroken;", null, null).visitEnd()
        writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
            visitCode()
            visitTypeInsn(Opcodes.NEW, "java/lang/Object")
            visitInsn(Opcodes.DUP)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitFieldInsn(Opcodes.PUTSTATIC, "Broken", "instance", "LBroken;")
            visitInsn(Opcodes.RETURN)
            visitMaxs(2, 0)
            visitEnd()
        }
        writer.visitEnd()
        val error = assertThrows(IllegalArgumentException::class.java) {
            TachiyomiExtensionConverter.rejectInvalidConstructorLowering(writer.toByteArray(), "real-shape.apk")
        }
        assertTrue(error.message!!.contains("Unsupported DEX constructor lowering"))
    }
}
