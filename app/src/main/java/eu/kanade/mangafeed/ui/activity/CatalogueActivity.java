package eu.kanade.mangafeed.ui.activity;

import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.presenter.CataloguePresenter;
import eu.kanade.mangafeed.view.CatalogueView;
import eu.kanade.mangafeed.widget.EndlessScrollListener;
import uk.co.ribot.easyadapter.EasyAdapter;

public class CatalogueActivity extends BaseActivity implements CatalogueView {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.gridView)
    GridView manga_list;

    @Bind(R.id.progress)
    ProgressBar progress;

    @Bind(R.id.progress_grid)
    ProgressBar progress_grid;

    private CataloguePresenter presenter;

    private EndlessScrollListener scroll_listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalogue);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        presenter = new CataloguePresenter(this);
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

    // CatalogueView

    @Override
    public void setTitle(String title) {
        setToolbarTitle(title);
    }

    @Override
    public void setAdapter(EasyAdapter adapter) {
        manga_list.setAdapter(adapter);
    }

    @Override
    public void setMangaClickListener() {
        manga_list.setOnItemClickListener(
                (parent, view, position, id) ->
                        presenter.onMangaClick(position)
        );
    }

    @Override
    public void setScrollListener() {
        scroll_listener = new EndlessScrollListener() {
            @Override
            public boolean onLoadMore(int page, int totalItemsCount) {
                presenter.loadMoreMangas(page);
                return true;
            }
        };

        manga_list.setOnScrollListener(scroll_listener);
    }

    @Override
    public void resetScrollListener() {
        scroll_listener.resetScroll();
    }

    @Override
    public void showProgressBar() {
        progress.setVisibility(ProgressBar.VISIBLE);
    }

    @Override
    public void showGridProgressBar() {
        progress_grid.setVisibility(ProgressBar.VISIBLE);
    }

    @Override
    public void hideProgressBar() {
        progress.setVisibility(ProgressBar.GONE);
        progress_grid.setVisibility(ProgressBar.GONE);
    }

    @Override
    public ImageView getImageView(int position) {
        View v = manga_list.getChildAt(position -
                manga_list.getFirstVisiblePosition());

        if(v == null)
            return null;

        return (ImageView) v.findViewById(R.id.catalogue_thumbnail);
    }
}
