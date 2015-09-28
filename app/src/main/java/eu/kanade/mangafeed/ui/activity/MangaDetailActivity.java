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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manga_detail);
        ButterKnife.bind(this);
        presenter = new MangaDetailPresenter(this);

        setupToolbar(toolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_manga_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static Intent newIntent(Context context, Manga manga) {
        Intent intent = new Intent(context, MangaDetailActivity.class);
        MangaDetailPresenter.newIntent(manga);
        return intent;
    }

    @Override
    public void onStart() {
        super.onStart();
        presenter.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        presenter.onStop();
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
