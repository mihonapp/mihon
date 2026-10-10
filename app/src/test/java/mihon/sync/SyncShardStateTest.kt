package mihon.sync

import io.kotest.matchers.shouldBe
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.drive.DriveFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SyncShardStateTest {

    private val hello = "5d41402abc4b2a76b9719d911017c592"
    private val world = "7d793037a0760186574b0282f2f435e7"

    private val known = SyncShardState(fileId = "shard", remoteVersion = "12", remoteMd5 = hello)

    @Test
    fun `a version bumped with the content unchanged is not a change`() {
        known.matches(DriveFile(id = "shard", version = "13", md5Checksum = hello)) shouldBe true
    }

    @Test
    fun `new content is a change`() {
        known.matches(DriveFile(id = "shard", version = "13", md5Checksum = world)) shouldBe false
    }

    @Test
    fun `without a checksum the version decides`() {
        val legacy = known.copy(remoteMd5 = "")
        legacy.matches(DriveFile(id = "shard", version = "12")) shouldBe true
        legacy.matches(DriveFile(id = "shard", version = "13", md5Checksum = hello)) shouldBe false
    }

    @Test
    fun `the checksum is the one Drive reports`() {
        SyncCodec(ProtoBuf).md5("hello".toByteArray()) shouldBe hello
    }
}
