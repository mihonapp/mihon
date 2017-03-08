package exh.ui.migration

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.Html
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.toast
import uy.kohesive.injekt.injectLazy

/**
 * Guide to migrate thel ibrary between two TachiyomiEH apps
 */

class LibraryMigrationManager(val context: MainActivity,
                              val dismissQueue: MutableList<DialogInterface>? = null) {
    val preferenceHelper: PreferencesHelper by injectLazy()

    val databaseHelper: DatabaseHelper by injectLazy()

    private fun mainTachiyomiEHActivity()
            = context.packageManager.getLaunchIntentForPackage(TACHIYOMI_EH_PACKAGE)

    fun askMigrationIfNecessary() {
        //Check already migrated
        val ms = preferenceHelper.migrationStatus().getOrDefault()
        if(ms == MigrationStatus.COMPLETED) return

        val ma = mainTachiyomiEHActivity()

        //Old version not installed, migration not required
        if(ma == null) {
            preferenceHelper.migrationStatus().set(MigrationStatus.COMPLETED)
            return
        }

        context.requestPermissionsOnMarshmallow()
        if(ms == MigrationStatus.NOT_INITIALIZED) {
            //We need migration
            jumpToMigrationStep(MigrationStatus.NOTIFY_USER)
        } else {
            //Migration process already started, jump to step
            jumpToMigrationStep(ms)
        }
    }

    fun notifyUserMigration() {
        redDialog()
                .title("Migration necessary")
                .content("Due to an unplanned technical error, this update could not be applied on top of the old app and was instead installed as a separate app!\n\n" +
                        "To keep your library/favorited galleries after this update, you must migrate it over from the old app.\n\n" +
                        "This migration process is not automatic, tap 'CONTINUE' to be guided through it.")
                .positiveText("Continue")
                .negativeText("Cancel")
                .onPositive { _, _ -> jumpToMigrationStep(MigrationStatus.OPEN_BACKUP_MENU) }
                .onNegative { _, _ -> warnUserMigration() }
                .show()
    }

    fun warnUserMigration() {
        redDialog()
                .title("Are you sure?")
                .content("You are cancelling the migration process! If you do not migrate your library, you will lose all of your favorited galleries!\n\n" +
                        "Press 'MIGRATE' to restart the migration process, press 'OK' if you still wish to cancel the migration process.")
                .positiveText("Ok")
                .negativeText("Migrate")
                .onPositive { _, _ -> completeMigration() }
                .onNegative { _, _ -> notifyUserMigration() }
                .show()
    }

    fun openBackupMenuMigrationStep() {
        val view = MigrationViewBuilder()
                .text("1. Use the 'LAUNCH OLD APP' button below to launch the old app.")
                .text("2. Tap on the 'three-lines' button at the top-left of the screen as shown below:")
                .image(R.drawable.eh_migration_hamburgers)
                .text("3. Highlight the 'Backup' item by tapping on it as shown below:")
                .image(R.drawable.eh_migration_backup)
                .text("4. Return to this app but <b>do not close</b> the old app.")
                .text("5. When you have completed the above steps, tap 'CONTINUE'.")
                .toView(context)

        migrationStepDialog(1, null, MigrationStatus.PERFORM_BACKUP)
                .customView(view, true)
                .neutralText("Launch Old App")
                .onNeutral { _, _ ->
                    //Auto dismiss messes this up so we have to reopen the dialog manually
                    val ma = mainTachiyomiEHActivity()
                    if(ma != null) {
                        context.startActivity(ma)
                    } else {
                        context.toast("Failed to launch old app! Try launching it manually.")
                    }
                    openBackupMenuMigrationStep()
                }
                .show()
    }

    fun performBackupMigrationStep() {
        val view = MigrationViewBuilder()
                .text("6. Return to the old app.")
                .text("7. Tap on the 'BACKUP' button in the old app (shown below):")
                .image(R.drawable.eh_migration_backup_button)
                .text("8. In the menu that appears, tap on 'Complete migration' (shown below):")
                .image(R.drawable.eh_migration_share_icon)
                .toView(context)

        migrationStepDialog(2, MigrationStatus.OPEN_BACKUP_MENU, null)
                .customView(view, true)
                .show()
    }

    fun finalizeMigration() {
        migrationDialog()
                .title("Migration complete")
                .content(fromHtmlCompat("Your library has been migrated over to the new app!<br><br>" +
                        "You may now uninstall the old app by pressing the 'UNINSTALL OLD APP' button below!<br><br>" +
                        "<b>If you were previously using ExHentai, your library may appear blank, just log in again to fix this.</b><br><br>" +
                        "Then tap 'OK' to exit the migration process!"))
                .positiveText("Ok")
                .neutralText("Uninstall Old App")
                .onPositive { _, _ ->
                        completeMigration()
                        //Check if the metadata needs to be updated
                        databaseHelper.getLibraryMangas().asRxSingle().subscribe {
                            if (it.size > 0)
                                context.runOnUiThread {
                                    MetadataFetchDialog().tryAskMigration(context)
                                }
                        }
                    }
                .onNeutral { _, _ ->
                    val packageUri = Uri.parse("package:$TACHIYOMI_EH_PACKAGE")
                    val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
                    context.startActivity(uninstallIntent)
                    //Cancel out auto-dismiss
                    finalizeMigration()
                }
                .show()
    }

    fun migrationDialog() = MaterialDialog.Builder(context)
            .cancelable(false)
            .canceledOnTouchOutside(false)
            .showListener { dismissQueue?.add(it) }!!

    fun migrationStepDialog(step: Int, previousStep: Int?, nextStep: Int?) = migrationDialog()
            .title("Migration part $step of ${MigrationStatus.MAX_MIGRATION_STEPS}")
            .apply {
                if(previousStep != null) {
                    negativeText("Back")
                    onNegative { _, _ -> jumpToMigrationStep(previousStep) }
                }
                if(nextStep != null) {
                    positiveText("Continue")
                    onPositive { _, _ -> jumpToMigrationStep(nextStep) }
                }
            }!!

    fun redDialog() = migrationDialog()
                .backgroundColor(Color.parseColor("#F44336"))
                .titleColor(Color.WHITE)
                .contentColor(Color.WHITE)
                .positiveColor(Color.WHITE)
                .negativeColor(Color.WHITE)
                .neutralColor(Color.WHITE)!!

    fun completeMigration() {
        preferenceHelper.migrationStatus().set(MigrationStatus.COMPLETED)

        //Enable orientation changes again
        context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
    }

    fun jumpToMigrationStep(migrationStatus: Int) {
        preferenceHelper.migrationStatus().set(migrationStatus)

        //Too lazy to actually deal with orientation changes
        context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

        when(migrationStatus) {
            MigrationStatus.NOTIFY_USER -> notifyUserMigration()
            MigrationStatus.OPEN_BACKUP_MENU -> openBackupMenuMigrationStep()
            MigrationStatus.PERFORM_BACKUP -> performBackupMigrationStep()
            MigrationStatus.FINALIZE_MIGRATION -> finalizeMigration()
        }
    }

    companion object {
        const val TACHIYOMI_EH_PACKAGE = "eu.kanade.tachiyomi.eh"
        fun fromHtmlCompat(string: String)
                = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(string, Html.FROM_HTML_MODE_LEGACY)
        else
            Html.fromHtml(string)
    }

    class MigrationViewBuilder {
        val elements = mutableListOf<MigrationElement>()
        fun text(text: String) = apply { elements += TextElement(text) }
        fun image(drawable: Int) = apply { elements += ImageElement(drawable) }

        fun toView(context: Activity): View {
            val root = LinearLayout(context)
            val rootParams = root.layoutParams ?: ViewGroup.LayoutParams(0, 0)

            fun ViewGroup.LayoutParams.setup() = apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }

            fun dpToPx(dp: Float) = (dp * context.resources.displayMetrics.density + 0.5f).toInt()

            rootParams.setup()
            root.layoutParams = rootParams
            root.gravity = Gravity.CENTER
            root.orientation = LinearLayout.VERTICAL

            for(element in elements) {
                val view: View
                if(element is TextElement) {
                    view = TextView(context)
                    view.text = fromHtmlCompat(element.value)
                } else if(element is ImageElement) {
                    view = ImageView(context)
                    view.setImageResource(element.drawable)
                    view.adjustViewBounds = true
                } else {
                    throw IllegalArgumentException("Unknown migration view!")
                }
                val viewParams = view.layoutParams ?: ViewGroup.LayoutParams(0, 0)
                viewParams.setup()
                view.layoutParams = viewParams
                val eightDpAsPx = dpToPx(8f)
                view.setPadding(0, eightDpAsPx, 0, eightDpAsPx)

                root.addView(view)
            }

            return root
        }
    }

    open class MigrationElement
    class TextElement(val value: String): MigrationElement()
    class ImageElement(val drawable: Int): MigrationElement()
}

