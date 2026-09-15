package android.app

/** Android Activity UIs cannot be created inside the desktop extension process. */
open class Activity : android.content.ContextWrapper(null) {
    init {
        throw UnsupportedOperationException("Android Activity UI is unsupported in desktop extensions")
    }
}
