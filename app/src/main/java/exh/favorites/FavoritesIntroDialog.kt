package exh.favorites

import android.content.Context
import android.text.Html
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

class FavoritesIntroDialog {
    private val prefs: PreferencesHelper by injectLazy()

    fun show(context: Context) = MaterialDialog.Builder(context)
            .title("IMPORTANT FAVORITES SYNC NOTES")
            .content(Html.fromHtml(FAVORITES_INTRO_TEXT))
            .positiveText("Ok")
            .onPositive { _, _ ->
                prefs.eh_showSyncIntro().set(false)
            }
            .cancelable(false)
            .show()

    private val FAVORITES_INTRO_TEXT = """
        1. Changes to category names in the app are <b>NOT</b> synced! Please <i>change the category names on ExHentai instead</i>. The category names will be copied from the ExHentai servers every sync.
        <br><br>
        2. The favorite categories on ExHentai correspond to the <b>first 10 categories in the app</b> (excluding the 'Default' category). <i>Galleries in other categories will <b>NOT</b> be synced!</i>
        <br><br>
        3. <font color='red'><b>ENSURE YOU HAVE A STABLE INTERNET CONNECTION WHEN SYNC IS IN PROGRESS!</b></font> If the internet disconnects while the app is syncing, your favorites may be left in a <i>partially-synced state</i>.
        <br><br>
        4. Keep the app open while favorites are syncing. Android will close apps that are in the background sometimes and that could be bad if it happens while the app is syncing.
        <br><br>
        5. <b>Do NOT put favorites in multiple categories</b> (the app supports this). This can confuse the sync algorithm as ExHentai only allows each favorite to be in one category.
        <br><br>
        This dialog will only popup once. You can read these notes again by going to 'Settings > E-Hentai > Show favorites sync notes'.
""".trimIndent()
}
