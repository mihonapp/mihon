package mihon.extension.host

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal object ExtensionBytecodeCompatibility {
    fun adapt(bytes: ByteArray): ByteArray? {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(reader, 0)
        var changed = false
        reader.accept(
            object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor = object : MethodVisitor(
                    Opcodes.ASM9,
                    super.visitMethod(access, name, descriptor, signature, exceptions),
                ) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        name: String,
                        descriptor: String,
                        isInterface: Boolean,
                    ) {
                        val temporaryFile = opcode == Opcodes.INVOKESTATIC && owner == "java/io/File" &&
                            name == "createTempFile" && descriptor in setOf(
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/io/File;",
                                "(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;",
                            )
                        if (temporaryFile) changed = true
                        super.visitMethodInsn(
                            opcode,
                            if (temporaryFile) "mihon/extension/host/ExtensionTemporaryFiles" else owner,
                            name,
                            descriptor,
                            isInterface,
                        )
                    }
                }
            },
            0,
        )
        return writer.toByteArray().takeIf { changed }
    }
}
