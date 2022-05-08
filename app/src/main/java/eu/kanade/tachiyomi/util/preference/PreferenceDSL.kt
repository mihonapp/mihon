package eu.kanade.tachiyomi.util.preference

import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.preference.AdaptiveTitlePreferenceCategory
import eu.kanade.tachiyomi.widget.preference.IntListPreference
import eu.kanade.tachiyomi.widget.preference.SwitchPreferenceCategory

@DslMarker
@Target(AnnotationTarget.TYPE)
annotation class DSL

inline fun PreferenceManager.newScreen(block: (@DSL PreferenceScreen).() -> Unit): PreferenceScreen {
    return createPreferenceScreen(context).also { it.block() }
}

inline fun PreferenceGroup.preference(block: (@DSL Preference).() -> Unit): Preference {
    return initThenAdd(Preference(context), block)
}

inline fun PreferenceGroup.infoPreference(@StringRes infoRes: Int): Preference {
    return add(
        Preference(context).apply {
            iconRes = R.drawable.ic_info_24dp
            iconTint = context.getResourceColor(android.R.attr.textColorHint)
            summaryRes = infoRes
            isSelectable = false
        },
    )
}

inline fun PreferenceGroup.switchPreference(block: (@DSL SwitchPreferenceCompat).() -> Unit): SwitchPreferenceCompat {
    return initThenAdd(SwitchPreferenceCompat(context), block)
}

inline fun PreferenceGroup.switchPreferenceCategory(block: (@DSL SwitchPreferenceCategory).() -> Unit): SwitchPreferenceCategory {
    return initThenAdd(SwitchPreferenceCategory(context), block)
}

inline fun PreferenceGroup.checkBoxPreference(block: (@DSL CheckBoxPreference).() -> Unit): CheckBoxPreference {
    return initThenAdd(CheckBoxPreference(context), block)
}

inline fun PreferenceGroup.editTextPreference(block: (@DSL EditTextPreference).() -> Unit): EditTextPreference {
    return initThenAdd(EditTextPreference(context), block)
}

inline fun PreferenceGroup.listPreference(block: (@DSL ListPreference).() -> Unit): ListPreference {
    return initThenAdd(ListPreference(context), block)
}

inline fun PreferenceGroup.intListPreference(block: (@DSL IntListPreference).() -> Unit): IntListPreference {
    return initThenAdd(IntListPreference(context), block)
}

inline fun PreferenceGroup.multiSelectListPreference(block: (@DSL MultiSelectListPreference).() -> Unit): MultiSelectListPreference {
    return initThenAdd(MultiSelectListPreference(context), block)
}

inline fun PreferenceScreen.preferenceCategory(block: (@DSL PreferenceCategory).() -> Unit): PreferenceCategory {
    return addThenInit(AdaptiveTitlePreferenceCategory(context), block)
}

inline fun PreferenceScreen.preferenceScreen(block: (@DSL PreferenceScreen).() -> Unit): PreferenceScreen {
    return addThenInit(preferenceManager.createPreferenceScreen(context), block)
}

inline fun <P : Preference> PreferenceGroup.add(p: P): P {
    return p.apply {
        this.isIconSpaceReserved = false
        this.isSingleLineTitle = false
        addPreference(this)
    }
}

inline fun <P : Preference> PreferenceGroup.initThenAdd(p: P, block: P.() -> Unit): P {
    return p.apply {
        block()
        this.isIconSpaceReserved = false
        this.isSingleLineTitle = false
        addPreference(this)
    }
}

inline fun <P : Preference> PreferenceGroup.addThenInit(p: P, block: P.() -> Unit): P {
    return p.apply {
        this.isIconSpaceReserved = false
        this.isSingleLineTitle = false
        addPreference(this)
        block()
    }
}

inline fun <T> Preference.bindTo(preference: com.fredporciuncula.flow.preferences.Preference<T>) {
    key = preference.key
    defaultValue = preference.defaultValue
}

inline fun <T> ListPreference.bindTo(preference: com.fredporciuncula.flow.preferences.Preference<T>) {
    key = preference.key
    // ListPreferences persist values as strings, even when we're using our IntListPreference
    defaultValue = preference.defaultValue.toString()
}

inline fun Preference.onClick(crossinline block: () -> Unit) {
    setOnPreferenceClickListener { block(); true }
}

inline fun Preference.onChange(crossinline block: (Any?) -> Boolean) {
    setOnPreferenceChangeListener { _, newValue -> block(newValue) }
}

inline fun SwitchPreferenceCompat.requireAuthentication(activity: FragmentActivity?, title: String, subtitle: String?) {
    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
        if (context.isAuthenticationSupported()) {
            activity?.startAuthentication(
                title,
                subtitle,
                callback = object : AuthenticatorUtil.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        activity: FragmentActivity?,
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        super.onAuthenticationSucceeded(activity, result)
                        isChecked = newValue as Boolean
                    }

                    override fun onAuthenticationError(
                        activity: FragmentActivity?,
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(activity, errorCode, errString)
                        activity?.toast(errString.toString())
                    }
                },
            )
        }

        false
    }
}

var Preference.defaultValue: Any?
    get() = null // set only
    set(value) {
        setDefaultValue(value)
    }

var Preference.titleRes: Int
    get() = 0 // set only
    set(value) {
        setTitle(value)
    }

var Preference.iconRes: Int
    get() = 0 // set only
    set(value) {
        icon = AppCompatResources.getDrawable(context, value)
    }

var Preference.summaryRes: Int
    get() = 0 // set only
    set(value) {
        setSummary(value)
    }

var Preference.iconTint: Int
    get() = 0 // set only
    set(value) {
        icon?.setTint(value)
    }

var ListPreference.entriesRes: Array<Int>
    get() = emptyArray() // set only
    set(value) {
        entries = value.map { context.getString(it) }.toTypedArray()
    }

var MultiSelectListPreference.entriesRes: Array<Int>
    get() = emptyArray() // set only
    set(value) {
        entries = value.map { context.getString(it) }.toTypedArray()
    }
