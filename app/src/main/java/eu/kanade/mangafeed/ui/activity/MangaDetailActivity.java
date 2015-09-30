package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.MangaDetailPresenter;
import eu.kanade.mangafeed.ui.adapter.ChapterListHolder;
import eu.kanade.mangafeed.view.MangaDetailView;
import uk.co.ribot.easyadapter.EasyAdapter;

public class MangaDetailActivity extends BaseActivity implements MangaDetailView {

    Manga manga;
    MangaDetailPresenter presenter;
    EasyAdapter<Chapter> adapter;

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.manga_chapters_list)
    ListView list_chapters;

    public static Intent newIntent(Context context, Manga manga) {
        Intent intent = new Intent(context, MangaDetailActivity.class);
        MangaDetailPresenter.newIntent(manga);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manga_detail);
        ButterKnife.bind(this);
        presenter = new MangaDetailPresenter(this);

        setupToolbar(toolbar);
    }

    @Override
    public void onStart() {
        super.onStart();
        presenter.registerForStickyEvents();
    }

    @Override
    public void onStop() {
        presenter.unregisterForEvents();
        super.onStop();
    }

    public void loadManga(Manga manga) {
        setToolbarTitle(manga.title);
    }

    public void setChapters(List<Chapter> chapters) {
        if (adapter == null) {
            adapter = new EasyAdapter<Chapter>(
                    getActivity(),
                    ChapterListHolder.class,
                    chapters
            );
            list_chapters.setAdapter(adapter);
        } else {
            adapter.setItems(chapters);
        }
    }

}
