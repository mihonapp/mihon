package mihon.desktop.extension.compat

import com.googlecode.d2j.Method
import com.googlecode.d2j.node.DexFileNode
import com.googlecode.d2j.node.insn.ConstStmtNode
import com.googlecode.d2j.node.insn.DexStmtNode
import com.googlecode.d2j.node.insn.FieldStmtNode
import com.googlecode.d2j.node.insn.MethodStmtNode
import com.googlecode.d2j.node.insn.Stmt1RNode
import com.googlecode.d2j.node.insn.Stmt2R1NNode
import com.googlecode.d2j.node.insn.Stmt2RNode
import com.googlecode.d2j.node.insn.Stmt3RNode
import com.googlecode.d2j.node.insn.TypeStmtNode
import com.googlecode.d2j.reader.BaseDexFileReader
import com.googlecode.d2j.reader.DexFileReader
import com.googlecode.d2j.reader.Op
import com.googlecode.d2j.visitors.DexFileVisitor

/** Restore eliminated constructors from original DEX allocation and inheritance facts. */
internal object DexConstructorNormalizer {
    fun reader(bytes: ByteArray): BaseDexFileReader {
        val node = DexFileNode()
        DexFileReader(bytes).accept(node)
        normalize(node)
        return object : BaseDexFileReader {
            override fun getDexVersion(): Int = node.dexVersion
            override fun getClassNames(): List<String> = node.clzs.map { it.className }
            override fun accept(visitor: DexFileVisitor) = node.accept(visitor)
            override fun accept(visitor: DexFileVisitor, config: Int) = node.accept(visitor)
            override fun accept(
                visitor: DexFileVisitor,
                classIdx: Int,
                config: Int,
            ) = node.clzs[classIdx].accept(visitor)
        }
    }

    fun normalize(node: DexFileNode) {
        val classes = node.clzs.associateBy { it.className }
        val generated = mutableSetOf<Method>()
        fun restore(type: String, parentConstructor: Method): Method {
            val target = classes[type] ?: error("Unsupported cross-DEX eliminated constructor: $type")
            require(target.superClass == parentConstructor.owner) {
                "Unsupported eliminated ancestor constructor: $type"
            }
            val restored = Method(type, "<init>", parentConstructor.parameterTypes, "V")
            require(restored in generated || target.methods.orEmpty().none { it.method == restored }) {
                "Unsupported eliminated constructor conflicts with an existing constructor: $type"
            }
            if (generated.add(restored)) {
                val slots = 1 + parentConstructor.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
                target.visitMethod(0x10001, restored).apply {
                    visitCode().apply {
                        visitRegister(slots)
                        visitMethodStmt(
                            if (slots <=
                                5
                            ) {
                                Op.INVOKE_DIRECT
                            } else {
                                Op.INVOKE_DIRECT_RANGE
                            },
                            IntArray(slots) { it },
                            parentConstructor,
                        )
                        visitStmt0R(Op.RETURN_VOID)
                        visitEnd()
                    }
                    visitEnd()
                }
            }
            return restored
        }
        for (owner in node.clzs) {
            for (method in owner.methods.orEmpty().toList()) {
                val code = method.codeNode ?: continue
                val meaningful = code.stmts.withIndex().filter { it.value.op != null }
                for ((position, first) in meaningful.withIndex()) {
                    val allocation = first.value as? TypeStmtNode ?: continue
                    if (allocation.op != Op.NEW_INSTANCE) continue
                    // Follow only straight-line argument setup. Stop at branches, escape or register overwrite.
                    var second: IndexedValue<DexStmtNode>? = null
                    for (candidate in meaningful.drop(position + 1)) {
                        val instruction = candidate.value
                        if (instruction is MethodStmtNode && instruction.method.name == "<init>" &&
                            instruction.args.firstOrNull() == allocation.a
                        ) {
                            second = candidate
                            break
                        }
                        val preservesAllocation = when (instruction) {
                            is ConstStmtNode -> instruction.a != allocation.a
                            is Stmt1RNode -> instruction.op.name.startsWith("MOVE_RESULT") &&
                                instruction.a != allocation.a
                            is Stmt2RNode -> instruction.a != allocation.a && instruction.b != allocation.a
                            is Stmt2R1NNode -> instruction.distReg != allocation.a && instruction.srcReg != allocation.a
                            is Stmt3RNode ->
                                instruction.a != allocation.a && instruction.b != allocation.a &&
                                    instruction.c != allocation.a
                            is TypeStmtNode -> instruction.a != allocation.a && instruction.b != allocation.a
                            is FieldStmtNode -> (
                                instruction.op.name.startsWith("IGET") ||
                                    instruction.op.name.startsWith("SGET")
                                ) &&
                                instruction.a != allocation.a &&
                                instruction.b != allocation.a
                            is MethodStmtNode -> allocation.a !in instruction.args
                            else -> false
                        }
                        if (!preservesAllocation) break
                    }
                    second ?: continue
                    val call = second.value as? MethodStmtNode ?: continue
                    if (call.op !in setOf(Op.INVOKE_DIRECT, Op.INVOKE_DIRECT_RANGE) || call.method.name != "<init>" ||
                        call.args.firstOrNull() != allocation.a || allocation.type == call.method.owner
                    ) {
                        continue
                    }
                    val restored = restore(allocation.type, call.method)
                    code.stmts[second.index] = MethodStmtNode(call.op, call.args, restored)
                }
                // R8 can also bypass a removed Object constructor on an intermediate superclass.
                if (method.method.name == "<init>" && owner.superClass != "Ljava/lang/Object;") {
                    val parameterSlots = method.method.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
                    val thisRegister = code.totalRegister - parameterSlots - 1
                    for ((index, instruction) in code.stmts.withIndex()) {
                        val call = instruction as? MethodStmtNode ?: continue
                        if (call.op == Op.INVOKE_DIRECT && call.method.owner == "Ljava/lang/Object;" &&
                            call.method.name == "<init>" && call.args.contentEquals(intArrayOf(thisRegister))
                        ) {
                            val restored = restore(owner.superClass, call.method)
                            code.stmts[index] = MethodStmtNode(call.op, call.args, restored)
                        }
                    }
                }
            }
        }
    }
}
