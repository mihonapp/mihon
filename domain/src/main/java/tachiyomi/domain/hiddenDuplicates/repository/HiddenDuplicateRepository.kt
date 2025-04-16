package tachiyomi.domain.hiddenDuplicates.repository

import tachiyomi.domain.manga.model.HiddenDuplicate

interface HiddenDuplicateRepository {

    suspend fun getAll(): List<HiddenDuplicate>
    suspend fun addHiddenDuplicate(id1: Long, id2: Long)
    suspend fun removeHiddenDuplicates(id1: Long, id2: Long)
}
