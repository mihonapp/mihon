package eu.kanade.tachiyomi.core.provider

import java.io.File

interface FolderProvider {

    fun directory(): File

    fun path(): String
}
