package eu.kanade.mangafeed.ui.activity;

import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.widget.ListView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.presenter.CatalogueListPresenter;
import eu.kanade.mangafeed.view.CatalogueListView;
import eu.kanade.mangafeed.widget.EndlessScrollListener;
import uk.co.ribot.easyadapter.EasyAdapter;

public class CatalogueListActivity extends BaseActivity implements CatalogueListView {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.catalogue_manga_list)
    ListView manga_list;

    private CatalogueListPresenter presenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalogue_list);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        presenter = new CatalogueListPresenter(this);
        presenter.initialize();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        presenter.destroySubscriptions();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.catalogue_list, menu);
        initializeSearch(menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void initializeSearch(Menu menu) {
        final SearchView sv = (SearchView) menu.findItem(R.id.action_search).getActionView();
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                presenter.onQueryTextChange(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                presenter.onQueryTextChange(newText);
                return true;
            }
        });
    }

    public void setSourceTitle(String title) {
        setToolbarTitle(title);
    }

    public void setAdapter(EasyAdapter adapter) {
        manga_list.setAdapter(adapter);
    }

    public void setScrollListener() {
        manga_list.setOnScrollListener(new EndlessScrollListener() {
            @Override
            public boolean onLoadMore(int page, int totalItemsCount) {
                presenter.loadMoreMangas(page);
                return true;
            }
        });
    }

}
