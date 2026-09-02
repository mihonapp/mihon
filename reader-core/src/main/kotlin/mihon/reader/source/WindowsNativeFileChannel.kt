package mihon.reader.source

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinNT.LARGE_INTEGER
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.W32APIOptions
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.NonWritableChannelException
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

internal object WindowsNativePathAccess {
    private const val OPEN_FLAGS = WinNT.FILE_FLAG_OPEN_REPARSE_POINT or WinNT.FILE_FLAG_BACKUP_SEMANTICS
    private const val SHARE_FLAGS = WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE or WinNT.FILE_SHARE_DELETE

    fun readMetadata(path: Path): WindowsPathMetadata {
        val handle = openHandle(path, desiredAccess = 0)
        return try {
            inspect(handle)
        } finally {
            closeHandle(handle)
        }
    }

    fun openFileChannel(path: Path): WindowsNativeFileChannel {
        val handle = openHandle(path, WinNT.GENERIC_READ)
        return try {
            val metadata = inspect(handle)
            if (!metadata.attributes.isRegularFile) throw IOException("Path is not a regular file")
            WindowsNativeFileChannel(handle, metadata.identity)
        } catch (error: Throwable) {
            closeHandle(handle)
            throw error
        }
    }

    fun listDirectory(
        path: Path,
        maxEntries: Int = ReaderLimits.MAX_ENTRIES,
    ): WindowsDirectorySnapshot {
        require(maxEntries >= 0) { "maxEntries must not be negative" }
        val handle = openHandle(path, WinNT.FILE_LIST_DIRECTORY)
        return try {
            val metadata = inspect(handle)
            if (!metadata.attributes.isDirectory) throw IOException("Path is not a directory")
            WindowsDirectorySnapshot(metadata.identity, readDirectoryNames(handle, maxEntries))
        } finally {
            closeHandle(handle)
        }
    }

    fun inspect(handle: HANDLE): WindowsPathMetadata {
        val attributeInfo = WinBase.FILE_ATTRIBUTE_TAG_INFO()
        getInformation(handle, WinBase.FileAttributeTagInfo, attributeInfo.pointer, attributeInfo.size())
        attributeInfo.read()
        if (attributeInfo.FileAttributes and WinNT.FILE_ATTRIBUTE_REPARSE_POINT != 0) {
            throw IOException("Symbolic link or reparse-point component")
        }

        val fileInformation = ByHandleFileInformation()
        if (!WindowsFileFunctions.INSTANCE.GetFileInformationByHandle(handle, fileInformation)) {
            throw windowsError("GetFileInformationByHandle")
        }
        fileInformation.read()
        val identity = WindowsFileIdentity(
            fileInformation.volumeSerialNumber.toLong() and 0xFFFF_FFFFL,
            fileInformation.fileIndexHigh.toLong().shl(32) or
                (fileInformation.fileIndexLow.toLong() and 0xFFFF_FFFFL),
        )

        val basicInfo = WinBase.FILE_BASIC_INFO()
        getInformation(handle, WinBase.FileBasicInfo, basicInfo.pointer, basicInfo.size())
        basicInfo.read()
        val standardInfo = WinBase.FILE_STANDARD_INFO()
        getInformation(handle, WinBase.FileStandardInfo, standardInfo.pointer, standardInfo.size())
        standardInfo.read()
        return WindowsPathMetadata(
            identity,
            WindowsBasicFileAttributes(identity, basicInfo, standardInfo),
        )
    }

    internal fun closeHandle(handle: HANDLE) {
        if (!Kernel32.INSTANCE.CloseHandle(handle)) throw windowsError("CloseHandle")
    }

    private fun openHandle(path: Path, desiredAccess: Int): HANDLE {
        val handle = Kernel32.INSTANCE.CreateFile(
            path.toString(),
            desiredAccess,
            SHARE_FLAGS,
            null,
            WinNT.OPEN_EXISTING,
            OPEN_FLAGS,
            null,
        )
        if (Pointer.nativeValue(handle.pointer) == Pointer.nativeValue(WinBase.INVALID_HANDLE_VALUE.pointer)) {
            throw windowsError("CreateFileW")
        }
        return handle
    }

    private fun getInformation(handle: HANDLE, informationClass: Int, pointer: Pointer, size: Int) {
        if (!Kernel32.INSTANCE.GetFileInformationByHandleEx(handle, informationClass, pointer, DWORD(size.toLong()))) {
            throw windowsError("GetFileInformationByHandleEx")
        }
    }

    private fun readDirectoryNames(handle: HANDLE, maxEntries: Int): List<String> {
        val names = mutableListOf<String>()
        val buffer = Memory(DIRECTORY_BUFFER_BYTES.toLong())
        var informationClass = WinBase.FileIdBothDirectoryRestartInfo
        while (true) {
            buffer.clear()
            if (!Kernel32.INSTANCE.GetFileInformationByHandleEx(
                    handle,
                    informationClass,
                    buffer,
                    DWORD(DIRECTORY_BUFFER_BYTES.toLong()),
                )
            ) {
                val code = Native.getLastError()
                if (code == WinError.ERROR_NO_MORE_FILES) break
                throw windowsError("GetFileInformationByHandleEx(directory)")
            }
            var offset = 0L
            while (true) {
                val nextOffset = buffer.getInt(offset + DIRECTORY_NEXT_OFFSET).toLong() and 0xFFFF_FFFFL
                val nameBytes = buffer.getInt(offset + DIRECTORY_NAME_LENGTH)
                if (nameBytes < 0 || nameBytes % 2 != 0 || offset + DIRECTORY_NAME_OFFSET + nameBytes > buffer.size()) {
                    throw IOException("Invalid Windows directory entry")
                }
                val name = String(buffer.getCharArray(offset + DIRECTORY_NAME_OFFSET, nameBytes / 2))
                if (name != "." && name != "..") {
                    if (names.size >= maxEntries) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
                    names += name
                }
                if (nextOffset == 0L) break
                if (nextOffset < DIRECTORY_NAME_OFFSET || offset + nextOffset >= buffer.size()) {
                    throw IOException("Invalid Windows directory entry offset")
                }
                offset += nextOffset
            }
            informationClass = WinBase.FileIdBothDirectoryInfo
        }
        return names
    }
}

internal data class WindowsFileIdentity(
    val volumeSerialNumber: Long,
    val fileIndex: Long,
)

internal data class WindowsPathMetadata(
    val identity: WindowsFileIdentity,
    val attributes: BasicFileAttributes,
)

internal data class WindowsDirectorySnapshot(
    val identity: WindowsFileIdentity,
    val names: List<String>,
)

internal class WindowsNativeFileChannel(
    private val handle: HANDLE,
    val identity: WindowsFileIdentity,
) : FileChannel() {
    private val ioLock = Any()

    override fun read(destination: ByteBuffer): Int = synchronized(ioLock) {
        ensureOpen()
        val bytes = ByteArray(minOf(destination.remaining(), MAX_READ_BYTES))
        if (bytes.isEmpty()) return 0
        val count = IntByReference()
        if (!Kernel32.INSTANCE.ReadFile(handle, bytes, bytes.size, count, null)) throw windowsError("ReadFile")
        val read = count.value
        if (read == 0) return -1
        destination.put(bytes, 0, read)
        read
    }

    override fun read(destinations: Array<out ByteBuffer>, offset: Int, length: Int): Long {
        requireRange(destinations.size, offset, length)
        var total = 0L
        for (index in offset until offset + length) {
            val read = read(destinations[index])
            if (read < 0) return if (total == 0L) -1 else total
            total += read
            if (destinations[index].hasRemaining()) break
        }
        return total
    }

    override fun read(destination: ByteBuffer, position: Long): Int = synchronized(ioLock) {
        require(position >= 0) { "position must not be negative" }
        val original = position()
        try {
            position(position)
            read(destination)
        } finally {
            position(original)
        }
    }

    override fun position(): Long = synchronized(ioLock) {
        ensureOpen()
        seek(0, FILE_CURRENT)
    }

    override fun position(newPosition: Long): FileChannel = synchronized(ioLock) {
        require(newPosition >= 0) { "position must not be negative" }
        ensureOpen()
        seek(newPosition, FILE_BEGIN)
        this
    }

    override fun size(): Long = synchronized(ioLock) {
        ensureOpen()
        val result = LARGE_INTEGER.ByReference()
        if (!WindowsFileFunctions.INSTANCE.GetFileSizeEx(handle, result)) throw windowsError("GetFileSizeEx")
        result.read()
        result.value
    }

    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long {
        require(position >= 0 && count >= 0) { "position and count must not be negative" }
        var transferred = 0L
        val buffer = ByteBuffer.allocate(minOf(count, MAX_READ_BYTES.toLong()).toInt())
        while (transferred < count) {
            buffer.clear()
            buffer.limit(minOf(buffer.capacity().toLong(), count - transferred).toInt())
            val read = read(buffer, position + transferred)
            if (read < 0) break
            buffer.flip()
            while (buffer.hasRemaining()) target.write(buffer)
            transferred += read
        }
        return transferred
    }

    override fun force(metaData: Boolean) = Unit

    override fun write(source: ByteBuffer): Int = throw NonWritableChannelException()

    override fun write(sources: Array<out ByteBuffer>, offset: Int, length: Int): Long =
        throw NonWritableChannelException()

    override fun write(source: ByteBuffer, position: Long): Int = throw NonWritableChannelException()

    override fun truncate(size: Long): FileChannel = throw NonWritableChannelException()

    override fun transferFrom(source: ReadableByteChannel, position: Long, count: Long): Long =
        throw NonWritableChannelException()

    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer =
        throw UnsupportedOperationException("Memory mapping is not supported for guarded native files")

    override fun lock(position: Long, size: Long, shared: Boolean): FileLock =
        throw UnsupportedOperationException("File locking is not supported for guarded native files")

    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock =
        throw UnsupportedOperationException("File locking is not supported for guarded native files")

    override fun implCloseChannel() = synchronized(ioLock) {
        WindowsNativePathAccess.closeHandle(handle)
    }

    private fun seek(distance: Long, origin: Int): Long {
        val high = IntByReference((distance ushr 32).toInt())
        Native.setLastError(0)
        val low = WindowsFileFunctions.INSTANCE.SetFilePointer(handle, distance.toInt(), high, origin)
        if (low == INVALID_SET_FILE_POINTER && Native.getLastError() != 0) {
            throw windowsError("SetFilePointer")
        }
        return high.value.toLong().shl(32) or (low.toLong() and 0xFFFF_FFFFL)
    }

    private fun ensureOpen() {
        if (!isOpen) throw ClosedChannelException()
    }
}

private class WindowsBasicFileAttributes(
    private val identity: WindowsFileIdentity,
    basic: WinBase.FILE_BASIC_INFO,
    standard: WinBase.FILE_STANDARD_INFO,
) : BasicFileAttributes {
    private val creation = windowsFileTime(basic.CreationTime.value)
    private val access = windowsFileTime(basic.LastAccessTime.value)
    private val modified = windowsFileTime(basic.LastWriteTime.value)
    private val directory = basic.FileAttributes and WinNT.FILE_ATTRIBUTE_DIRECTORY != 0
    private val byteCount = standard.EndOfFile.value

    override fun lastModifiedTime(): FileTime = modified

    override fun lastAccessTime(): FileTime = access

    override fun creationTime(): FileTime = creation

    override fun isRegularFile(): Boolean = !directory

    override fun isDirectory(): Boolean = directory

    override fun isSymbolicLink(): Boolean = false

    override fun isOther(): Boolean = false

    override fun size(): Long = byteCount

    override fun fileKey(): Any = identity
}

@Suppress("FunctionName")
private interface WindowsFileFunctions : Library {
    fun GetFileInformationByHandle(file: HANDLE, information: ByHandleFileInformation): Boolean

    fun SetFilePointer(
        file: HANDLE,
        distanceLow: Int,
        distanceHigh: IntByReference,
        moveMethod: Int,
    ): Int

    fun GetFileSizeEx(file: HANDLE, size: LARGE_INTEGER.ByReference): Boolean

    companion object {
        val INSTANCE: WindowsFileFunctions = Native.load(
            "kernel32",
            WindowsFileFunctions::class.java,
            W32APIOptions.UNICODE_OPTIONS,
        )
    }
}

@Suppress("VariableNaming")
@com.sun.jna.Structure.FieldOrder(
    "fileAttributes",
    "creationTime",
    "lastAccessTime",
    "lastWriteTime",
    "volumeSerialNumber",
    "fileSizeHigh",
    "fileSizeLow",
    "numberOfLinks",
    "fileIndexHigh",
    "fileIndexLow",
)
internal class ByHandleFileInformation : com.sun.jna.Structure() {
    @JvmField var fileAttributes: Int = 0

    @JvmField var creationTime: WinBase.FILETIME = WinBase.FILETIME()

    @JvmField var lastAccessTime: WinBase.FILETIME = WinBase.FILETIME()

    @JvmField var lastWriteTime: WinBase.FILETIME = WinBase.FILETIME()

    @JvmField var volumeSerialNumber: Int = 0

    @JvmField var fileSizeHigh: Int = 0

    @JvmField var fileSizeLow: Int = 0

    @JvmField var numberOfLinks: Int = 0

    @JvmField var fileIndexHigh: Int = 0

    @JvmField var fileIndexLow: Int = 0
}

private fun windowsFileTime(value: Long): FileTime {
    val millisecondsSinceUnixEpoch = value / WINDOWS_TICKS_PER_MILLISECOND - WINDOWS_EPOCH_MILLISECONDS
    return FileTime.from(millisecondsSinceUnixEpoch, TimeUnit.MILLISECONDS)
}

private fun windowsError(operation: String): IOException {
    val code = Native.getLastError()
    return IOException("$operation failed with Win32 error $code: ${Kernel32Util.formatMessageFromLastErrorCode(code)}")
}

private fun requireRange(size: Int, offset: Int, length: Int) {
    if (offset < 0 || length < 0 || offset > size - length) throw IndexOutOfBoundsException()
}

private const val WINDOWS_TICKS_PER_MILLISECOND = 10_000L
private const val WINDOWS_EPOCH_MILLISECONDS = 11_644_473_600_000L
private const val FILE_BEGIN = 0
private const val FILE_CURRENT = 1
private const val INVALID_SET_FILE_POINTER = -1
private const val MAX_READ_BYTES = 1024 * 1024
private const val DIRECTORY_BUFFER_BYTES = 64 * 1024
private const val DIRECTORY_NEXT_OFFSET = 0L
private const val DIRECTORY_NAME_LENGTH = 60L
private const val DIRECTORY_NAME_OFFSET = 104L
