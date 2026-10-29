@file:Suppress("PackageDirectoryMismatch")

package androidx.preference

/**
 * Returns package-private [EditTextPreference.getOnBindEditTextListener]
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun EditTextPreference.getOnBindEditTextListener(): EditTextPreference.OnBindEditTextListener? {
    return onBindEditTextListener
}

/**
 * Calls [onChange] whenever this preference's state, visibility or children change, the package-private hook
 * [PreferenceGroupAdapter] uses to rebind.
 */
fun Preference.setOnStateChangeListener(onChange: (Preference) -> Unit) {
    setOnPreferenceChangeInternalListener(
        object : Preference.OnPreferenceChangeInternalListener {
            override fun onPreferenceChange(preference: Preference) = onChange(preference)
            override fun onPreferenceHierarchyChange(preference: Preference) = onChange(preference)
            override fun onPreferenceVisibilityChange(preference: Preference) = onChange(preference)
        },
    )
}
