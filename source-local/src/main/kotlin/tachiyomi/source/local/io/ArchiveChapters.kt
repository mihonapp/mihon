package tachiyomi.source.local.io

import mihon.core.archive.ArchiveReader
import tachiyomi.core.common.util.system.ImageUtil

/**
 * Returns the names of the archive's image entries, in archive order.
 */
fun ArchiveReader.getImageEntryNames(): List<String> = useEntries { entries ->
    entries
        .filter { it.isFile && ImageUtil.isImage(it.name) { getInputStream(it.name)!! } }
        .map { it.name }
        .toList()
}

/**
 * Returns the folders holding the pages at [pagePaths], one per chapter, when the pages are split into at least two
 * folders and none of them holds pages of its own besides another folder's. Otherwise null, as the archive is a single
 * chapter.
 */
fun getChapterFolders(pagePaths: List<String>): List<String>? {
    val folders = pagePaths.mapTo(HashSet()) { it.substringBeforeLast('/', missingDelimiterValue = "") }
    if (folders.size < 2 || "" in folders) return null
    if (folders.any { folder -> folders.any { it.startsWith("$folder/") } }) return null
    return folders.toList()
}
