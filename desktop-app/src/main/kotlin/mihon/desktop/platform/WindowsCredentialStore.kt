package mihon.desktop.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

interface CredentialStore {
    fun read(target: String): String?
    fun write(target: String, secret: String): Boolean
    fun delete(target: String): Boolean
}

/** Generic credentials are encrypted by Windows and scoped to the current Windows user. */
class WindowsCredentialStore : CredentialStore {
    @Suppress("ktlint:standard:function-naming") // Names are the Windows ABI entry points.
    private interface Credentials : StdCallLibrary {
        fun CredReadW(target: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
        fun CredWriteW(credential: Credential, flags: Int): Boolean
        fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean
        fun CredFree(pointer: Pointer)
    }

    @Structure.FieldOrder(
        "flags",
        "type",
        "target",
        "comment",
        "lastWritten",
        "size",
        "blob",
        "persist",
        "attributeCount",
        "attributes",
        "alias",
        "user",
    )
    class Credential : Structure {
        @JvmField var flags = 0

        @JvmField var type = 1

        @JvmField var target: WString? = null

        @JvmField var comment: WString? = null

        @JvmField var lastWritten = 0L

        @JvmField var size = 0

        @JvmField var blob: Pointer? = null

        @JvmField var persist = 2

        @JvmField var attributeCount = 0

        @JvmField var attributes: Pointer? = null

        @JvmField var alias: WString? = null

        @JvmField var user: WString? = null
        constructor() : super()
        constructor(pointer: Pointer) : super(pointer) {
            read()
        }
    }

    private val api by lazy { Native.load("Advapi32", Credentials::class.java) }

    override fun read(target: String): String? {
        val result = PointerByReference()
        if (!api.CredReadW(WString(target), 1, 0, result)) return null
        return try {
            val credential = Credential(result.value)
            credential.blob?.getByteArray(0, credential.size)?.toString(Charsets.UTF_8)
        } finally {
            api.CredFree(result.value)
        }
    }

    override fun write(target: String, secret: String): Boolean {
        val bytes = secret.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 2560) { "Credential exceeds Windows generic credential limit" }
        val memory = Memory(maxOf(1, bytes.size).toLong())
        return try {
            memory.write(0, bytes, 0, bytes.size)
            val credential = Credential().apply {
                this.target = WString(target)
                user = WString("MihonW")
                size = bytes.size
                blob = memory
            }
            api.CredWriteW(credential, 0)
        } finally {
            memory.clear()
            memory.close()
            bytes.fill(0)
        }
    }

    override fun delete(target: String): Boolean = api.CredDeleteW(WString(target), 1, 0)
}
