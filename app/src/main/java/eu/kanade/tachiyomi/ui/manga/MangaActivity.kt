package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.event.MangaEvent
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.ui.manga.chapter.ChaptersFragment
import eu.kanade.tachiyomi.ui.manga.info.MangaInfoFragment
import eu.kanade.tachiyomi.ui.manga.myanimelist.MyAnimeListFragment
import eu.kanade.tachiyomi.util.SharedData
import kotlinx.android.synthetic.main.activity_manga.*
import kotlinx.android.synthetic.main.toolbar.*
import nucleus.factory.RequiresPresenter

@RequiresPresenter(MangaPresenter::class)
class MangaActivity : BaseRxActivity<MangaPresenter>() {

    companion object {

        val FROM_CATALOGUE = "from_catalogue"
        val INFO_FRAGMENT = 0
        val CHAPTERS_FRAGMENT = 1
        val MYANIMELIST_FRAGMENT = 2

        fun newIntent(context: Context, manga: Manga): Intent {
            val intent = Intent(context, MangaActivity::class.java)
            SharedData.put(MangaEvent(manga))
            return intent
        }
    }

    private lateinit var adapter: MangaDetailAdapter

    var isCatalogueManga: Boolean = false
        private set

    override fun onCreate(savedState: Bundle?) {
        setAppTheme()
        super.onCreate(savedState)
        setContentView(R.layout.activity_manga)

        setupToolbar(toolbar)

        isCatalogueManga = intent.getBooleanExtra(FROM_CATALOGUE, false)

        adapter = MangaDetailAdapter(supportFragmentManager, this)
        view_pager.adapter = adapter

        tabs.setupWithViewPager(view_pager)

        if (!isCatalogueManga)
            view_pager.currentItem = CHAPTERS_FRAGMENT

        requestPermissionsOnMarshmallow()
    }

    fun onSetManga(manga: Manga) {
        setToolbarTitle(manga.title)
    }

    internal class MangaDetailAdapter(fm: FragmentManager, activity: MangaActivity) : FragmentPagerAdapter(fm) {

        private var pageCount: Int = 0
        private val tabTitles = arrayOf(activity.getString(R.string.manga_detail_tab),
                activity.getString(R.string.manga_chapters_tab), "MAL")

        init {
            pageCount = 2
            if (!activity.isCatalogueManga && activity.presenter.syncManager.myAnimeList.isLogged)
                pageCount++
        }

        override fun getCount(): Int {
            return pageCount
        }

        override fun getItem(position: Int): Fragment? {
            when (position) {
                INFO_FRAGMENT -> return MangaInfoFragment.newInstance()
                CHAPTERS_FRAGMENT -> return ChaptersFragment.newInstance()
                MYANIMELIST_FRAGMENT -> return MyAnimeListFragment.newInstance()
                else -> return null
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            // Generate title based on item position
            return tabTitles[position]
        }

    }

}
