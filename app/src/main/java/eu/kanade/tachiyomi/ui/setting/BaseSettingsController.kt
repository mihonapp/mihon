package eu.kanade.tachiyomi.ui.setting

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.openInBrowser

abstract class BaseSettingsController : SettingsController() {

    init {
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_help -> activity?.openInBrowser(URL_HELP)
        }

        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val URL_HELP = "https://tachiyomi.org/help/"
    }
}
