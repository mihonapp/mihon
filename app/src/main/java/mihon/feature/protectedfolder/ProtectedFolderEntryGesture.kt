package mihon.feature.protectedfolder

enum class ProtectedFolderEntryGesture {
    TitleLongPress,
    TitleDoubleTap,
}

/**
 * The gesture on the Library title that opens the protected folder's PIN pad.
 */
val ProtectedFolderEntry = ProtectedFolderEntryGesture.TitleLongPress
