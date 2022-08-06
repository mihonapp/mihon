package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializer

@Serializer(forClass = Backup::class)
object BackupSerializer
