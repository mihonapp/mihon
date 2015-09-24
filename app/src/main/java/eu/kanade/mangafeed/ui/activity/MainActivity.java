package eu.kanade.mangafeed.ui.activity;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;

import javax.inject.Inject;

import eu.kanade.mangafeed.R;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;
import uk.co.ribot.easyadapter.EasyRecyclerAdapter;

public class MainActivity extends BaseActivity {

    @Bind(R.id.recycler_characters)
    RecyclerView mCharactersRecycler;

    @Bind(R.id.toolbar)
    Toolbar mToolbar;

    @Bind(R.id.progress_indicator)
    ProgressBar mProgressBar;

    @Bind(R.id.swipe_container)
    SwipeRefreshLayout mSwipeRefresh;

    @Inject DatabaseHelper mDb;
    private CompositeSubscription mSubscriptions;
    private EasyRecyclerAdapter<Character> mEasyRecycleAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applicationComponent().inject(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mSubscriptions = new CompositeSubscription();
        //mDataManager = App.get(this).getComponent().dataManager();
        setupToolbar();
        setupRecyclerView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSubscriptions.unsubscribe();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_github:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupToolbar() {
        setSupportActionBar(mToolbar);
    }

    private void setupRecyclerView() {
        mCharactersRecycler.setLayoutManager(new LinearLayoutManager(this));
        mCharactersRecycler.setAdapter(mEasyRecycleAdapter);

        mSwipeRefresh.setColorSchemeResources(R.color.primary);
    }
}
