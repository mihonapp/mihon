package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;

public class MangaDetailActivity extends BaseActivity {

    Manga manga;

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manga_detail);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        EventBus.getDefault().registerSticky(this);
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

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);

        super.onDestroy();
    }

    public static Intent newIntent(Context context, Manga manga) {
        Intent intent = new Intent(context, MangaDetailActivity.class);
        EventBus.getDefault().postSticky(manga);
        return intent;
    }

    public void onEventMainThread(Manga manga) {
        this.manga = manga;
        loadManga();
        //loadChapters();
    }

    private void loadManga() {
        setToolbarTitle(manga.title);
    }
}
