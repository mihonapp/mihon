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

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.MangaDetailPresenter;
import eu.kanade.mangafeed.ui.fragment.MangaChaptersFragment;
import eu.kanade.mangafeed.ui.fragment.MangaInfoFragment;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(MangaDetailPresenter.class)
public class MangaDetailActivity extends BaseActivity<MangaDetailPresenter> {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.tabs)
    TabLayout tabs;

    @Bind(R.id.viewpager)
    ViewPager view_pager;

    long manga_id;

    public final static String MANGA_ID = "manga_id";
    public final static String MANGA_TITLE = "manga_title";

    public static Intent newIntent(Context context, Manga manga) {
        Intent intent = new Intent(context, MangaDetailActivity.class);
        intent.putExtra(MANGA_ID, manga.id);
        intent.putExtra(MANGA_TITLE, manga.title);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manga_detail);
        ButterKnife.bind(this);

        setupToolbar(toolbar);
        disableToolbarElevation();

        String manga_title = getIntent().getStringExtra(MANGA_TITLE);
        setToolbarTitle(manga_title);

        manga_id = getIntent().getLongExtra(MANGA_ID, -1);
        setupViewPager();
    }

    private void disableToolbarElevation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toolbar.setElevation(0);
        }
    }

    private void setupViewPager() {
        view_pager.setAdapter(new MangaDetailAdapter(
                getSupportFragmentManager(),
                getActivity(),
                manga_id));

        tabs.setupWithViewPager(view_pager);
    }

}

class MangaDetailAdapter extends FragmentPagerAdapter {

    final int PAGE_COUNT = 2;
    private String tab_titles[];
    private Context context;
    private long manga_id;

    public MangaDetailAdapter(FragmentManager fm, Context context, long manga_id) {
        super(fm);
        this.context = context;
        tab_titles = new String[]{
                context.getString(R.string.manga_detail_tab),
                context.getString(R.string.manga_chapters_tab)
        };
        this.manga_id = manga_id;
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return MangaInfoFragment.newInstance(manga_id);
            case 1:
                return MangaChaptersFragment.newInstance(manga_id);

            default: return null;
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        // Generate title based on item position
        return tab_titles[position];
    }
}
