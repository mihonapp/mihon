package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.MangaDetailPresenter;
import eu.kanade.mangafeed.ui.activity.base.BaseRxActivity;
import eu.kanade.mangafeed.ui.fragment.MangaChaptersFragment;
import eu.kanade.mangafeed.ui.fragment.MangaInfoFragment;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(MangaDetailPresenter.class)
public class MangaDetailActivity extends BaseRxActivity<MangaDetailPresenter> {

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.tabs) TabLayout tabs;
    @Bind(R.id.view_pager) ViewPager view_pager;

    private MangaDetailAdapter adapter;
    private long manga_id;
    private boolean is_online;
    private MenuItem favoriteBtn;

    public final static String MANGA_ID = "manga_id";
    public final static String MANGA_ONLINE = "manga_online";

    public static Intent newIntent(Context context, Manga manga) {
        Intent intent = new Intent(context, MangaDetailActivity.class);
        intent.putExtra(MANGA_ID, manga.id);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manga_detail);
        ButterKnife.bind(this);

        setupToolbar(toolbar);
        disableToolbarElevation();

        Intent intent = getIntent();

        manga_id = intent.getLongExtra(MANGA_ID, -1);
        is_online = intent.getBooleanExtra(MANGA_ONLINE, false);

        setupViewPager();

        if (savedInstanceState == null)
            getPresenter().queryManga(manga_id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manga, menu);
        favoriteBtn = menu.findItem(R.id.action_favorite);
        getPresenter().setFavoriteVisibility();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_favorite:
                onFavoriteClick();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void disableToolbarElevation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toolbar.setElevation(0);
        }
    }

    private void setupViewPager() {
        adapter = new MangaDetailAdapter(
                getSupportFragmentManager(),
                getActivity());

        view_pager.setAdapter(adapter);
        tabs.setupWithViewPager(view_pager);

        if (!is_online)
            view_pager.setCurrentItem(MangaDetailAdapter.CHAPTERS_FRAGMENT);
    }

    public long getMangaId() {
        return manga_id;
    }

    public void setManga(Manga manga) {
        setToolbarTitle(manga.title);
    }

    public void setFavoriteBtnVisible(boolean visible) {
        if (favoriteBtn != null)
            favoriteBtn.setVisible(visible);
    }

    public boolean isOnlineManga() {
        return is_online;
    }

    private void onFavoriteClick() {
        if (getPresenter().addToFavorites()) {
            Toast.makeText(this, getString(R.string.toast_added_favorites), Toast.LENGTH_SHORT)
                .show();
        }
    }

    class MangaDetailAdapter extends FragmentPagerAdapter {

        final int PAGE_COUNT = 2;
        private String tab_titles[];
        private Context context;

        final static int INFO_FRAGMENT = 0;
        final static int CHAPTERS_FRAGMENT = 1;

        public MangaDetailAdapter(FragmentManager fm, Context context) {
            super(fm);
            this.context = context;
            tab_titles = new String[]{
                    context.getString(R.string.manga_detail_tab),
                    context.getString(R.string.manga_chapters_tab)
            };
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case INFO_FRAGMENT:
                    return MangaInfoFragment.newInstance();
                case CHAPTERS_FRAGMENT:
                    return MangaChaptersFragment.newInstance();

                default:
                    return null;
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            // Generate title based on item position
            return tab_titles[position];
        }
    }

}
