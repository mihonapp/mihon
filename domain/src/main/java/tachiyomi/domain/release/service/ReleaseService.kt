package tachiyomi.domain.release.service

import tachiyomi.domain.release.model.Release

interface ReleaseService {

    suspend fun getRepository(key: String): String?

    suspend fun latest(repository: String): Release
}
