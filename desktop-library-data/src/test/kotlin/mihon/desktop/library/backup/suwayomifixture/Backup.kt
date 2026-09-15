@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.desktop.library.backup.suwayomifixture

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Official Backup common wire fields; filename helpers/server settings omitted.
@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(9000) val meta: Map<String, String> = emptyMap(),
)

@Serializable
enum class UpdateStrategy { ALWAYS_UPDATE, ONLY_FETCH_ONCE }
