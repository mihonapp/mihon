@file:Suppress("PackageDirectoryMismatch")

package androidx.preference

/**
 * Returns package-private [EditTextPreference.getOnBindEditTextListener]
 */
fun EditTextPreference.getOnBindEditTextListener(): EditTextPreference.OnBindEditTextListener? {
    return onBindEditTextListener
}
