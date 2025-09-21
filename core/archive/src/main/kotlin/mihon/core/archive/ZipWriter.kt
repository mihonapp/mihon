package mihon.core.archive

import android.content.Context
import android.system.Os
import android.system.StructStat
import com.hippo.unifile.UniFile
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.nio.ByteBuffer

// 644 in octal is 420 in decimal
const val READ_WRITE_PERM = 420

class ZipWriter(val context: Context, file: UniFile) : Closeable {
    private val pfd = file.openFileDescriptor(context, "wt")
    private val archive = Archive.writeNew()
    private val entry = ArchiveEntry.new2(archive)
    private val buffer = ByteBuffer.allocateDirect(8192)

    // TODO: because ArchiveEntry.setPathnameUtf8() allows nullable file names, we will allow nulls here.
    //  Unsure of subsequent behaviour if they really are nulls.
    private val _files = ArrayList<String?>()
    val files: List<String?>
        get() {
            // TODO: probably better to wrap it in an immutable list wrapper than to just cast like this to
            //  guarantee callers cannot modify this list.
            return _files
        }

    init {
        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
            Archive.writeSetFormatZip(archive)
            Archive.writeZipSetCompressionStore(archive)
            Archive.writeOpenFd(archive, pfd.fd)
        } catch (e: ArchiveException) {
            close()
            throw e
        }
    }

    fun write(file: UniFile) {
        file.openFileDescriptor(context, "r").use {
            val fd = it.fileDescriptor
            ArchiveEntry.clear(entry)
            ArchiveEntry.setPathnameUtf8(entry, file.name)
            val stat = Os.fstat(fd)
            ArchiveEntry.setStat(entry, stat.toArchiveStat())
            Archive.writeHeader(archive, entry)
            while (true) {
                buffer.clear()
                Os.read(fd, buffer)
                if (buffer.position() == 0) break
                buffer.flip()
                Archive.writeData(archive, buffer)
            }
            Archive.writeFinishEntry(archive)
        }
        _files.add(file.name)
    }

    fun write(filename: String, data: ByteArray) {
        ArchiveEntry.clear(entry)
        ArchiveEntry.setPathnameUtf8(entry, filename)
        ArchiveEntry.setSize(entry, data.size.toLong())
        ArchiveEntry.setFiletype(entry, ArchiveEntry.AE_IFREG)
        ArchiveEntry.setPerm(entry, READ_WRITE_PERM)
        Archive.writeHeader(archive, entry)
        Archive.writeData(archive, ByteBuffer.wrap(data))
        Archive.writeFinishEntry(archive)
        _files.add(filename)
    }

    override fun close() {
        ArchiveEntry.free(entry)
        Archive.writeFree(archive)
        pfd.close()
    }
}

private fun StructStat.toArchiveStat() = ArchiveEntry.StructStat().apply {
    stDev = st_dev
    stMode = st_mode
    stNlink = st_nlink.toInt()
    stUid = st_uid
    stGid = st_gid
    stRdev = st_rdev
    stSize = st_size
    stBlksize = st_blksize
    stBlocks = st_blocks
    stAtim = st_atime.toTimespec()
    stMtim = st_mtime.toTimespec()
    stCtim = st_ctime.toTimespec()
    stIno = st_ino
}

private fun Long.toTimespec() = ArchiveEntry.StructTimespec().also { it.tvSec = this }
