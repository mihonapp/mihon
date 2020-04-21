package eu.kanade.tachiyomi.ui.migration.manga.design

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.bluelinelabs.conductor.Controller
import com.f2prateek.rx.preferences.Preference
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import kotlinx.android.synthetic.main.migration_bottom_sheet.*
import kotlinx.android.synthetic.main.migration_bottom_sheet.extra_search_param
import kotlinx.android.synthetic.main.migration_bottom_sheet.extra_search_param_text
import kotlinx.android.synthetic.main.migration_bottom_sheet.mig_categories
import kotlinx.android.synthetic.main.migration_bottom_sheet.mig_chapters
import kotlinx.android.synthetic.main.migration_bottom_sheet.mig_tracking
import kotlinx.android.synthetic.main.migration_bottom_sheet.use_smart_search
import uy.kohesive.injekt.injectLazy

class MigrationBottomSheetDialog(
    activity: Activity,
    theme: Int,
    private val listener:
    StartMigrationListener
) :
    BottomSheetDialog(activity,
    theme) {
    /**
     * Preferences helper.
     */
    private val preferences by injectLazy<PreferencesHelper>()

    init {
        // Use activity theme for this layout
        val view = activity.layoutInflater.inflate(R.layout.migration_bottom_sheet, null)
        // val scroll = NestedScrollView(context)
        // scroll.addView(view)

        setContentView(view)
        if (activity.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE)
            sourceGroup.orientation = LinearLayout.HORIZONTAL
        window?.setBackgroundDrawable(null)
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPreferences()

        fab.setOnClickListener {
            preferences.skipPreMigration().set(skip_step.isChecked)
            listener.startMigration(
                if (use_smart_search.isChecked && extra_search_param_text.text.isNotBlank())
                    extra_search_param_text.text.toString() else null)
            dismiss()
        }
    }

    /**
     * Init general reader preferences.
     */
    private fun initPreferences() {
        val flags = preferences.migrateFlags().get()

        mig_chapters.isChecked = MigrationFlags.hasChapters(flags)
        mig_categories.isChecked = MigrationFlags.hasCategories(flags)
        mig_tracking.isChecked = MigrationFlags.hasTracks(flags)

        mig_chapters.setOnCheckedChangeListener { _, _ -> setFlags() }
        mig_categories.setOnCheckedChangeListener { _, _ -> setFlags() }
        mig_tracking.setOnCheckedChangeListener { _, _ -> setFlags() }

        use_smart_search.bindToPreference(preferences.smartMigration())
        extra_search_param_text.gone()
        extra_search_param.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                extra_search_param_text.visible()
            } else {
                extra_search_param_text.gone()
            }
        }
        sourceGroup.bindToPreference(preferences.useSourceWithMost())

        skip_step.isChecked = preferences.skipPreMigration().getOrDefault()
        skip_step.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked)
                (listener as? Controller)?.activity?.toast(R.string.pre_migration_skip_toast,
                    Toast.LENGTH_LONG)
        }
    }

    private fun setFlags() {
        var flags = 0
        if (mig_chapters.isChecked) flags = flags or MigrationFlags.CHAPTERS
        if (mig_categories.isChecked) flags = flags or MigrationFlags.CATEGORIES
        if (mig_tracking.isChecked) flags = flags or MigrationFlags.TRACK
        preferences.migrateFlags().set(flags)
    }

    /**
     * Binds a checkbox or switch view with a boolean preference.
     */
    private fun CompoundButton.bindToPreference(pref: Preference<Boolean>) {
        isChecked = pref.getOrDefault()
        setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
    }

    /**
     * Binds a radio group with a boolean preference.
     */
    private fun RadioGroup.bindToPreference(pref: Preference<Boolean>) {
        (getChildAt(pref.getOrDefault().toInt()) as RadioButton).isChecked = true
        setOnCheckedChangeListener { _, value ->
            val index = indexOfChild(findViewById(value))
            pref.set(index == 1)
        }
    }

    private fun Boolean.toInt() = if (this) 1 else 0
}

interface StartMigrationListener {
    fun startMigration(extraParam: String?)
}
