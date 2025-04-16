package tachiyomi.domain.hiddenDuplicates.repository

import tachiyomi.domain.manga.model.HiddenDuplicate

interface HiddenDuplicateRepository {

    suspend fun getAll(): List<HiddenDuplicate>
}
