package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.CataloguePresenter;
import eu.kanade.mangafeed.ui.adapter.CatalogueHolder;
import eu.kanade.mangafeed.widget.EndlessScrollListener;
import nucleus.factory.RequiresPresenter;
import uk.co.ribot.easyadapter.EasyAdapter;

@RequiresPresenter(CataloguePresenter.class)
public class CatalogueActivity extends BaseActivity<CataloguePresenter> {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.gridView)
    GridView manga_list;

    @Bind(R.id.progress)
    ProgressBar progress;

    @Bind(R.id.progress_grid)
    ProgressBar progress_grid;

    private EasyAdapter<Manga> adapter;
    private EndlessScrollListener scroll_listener;

    public final static String SOURCE_ID = "source_id";

    public static Intent newIntent(Context context, int source_id) {
        Intent intent = new Intent(context, CatalogueActivity.class);
        intent.putExtra(SOURCE_ID, source_id);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalogue);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        initializeAdapter();
        initializeClickListener();
        initializeScrollListener();
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
                getPresenter().onQueryTextChange(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                getPresenter().onQueryTextChange(newText);
                return true;
            }
        });
    }

    public EasyAdapter<Manga> getAdapter() {
        return adapter;
    }

    public void initializeAdapter() {
        adapter = new EasyAdapter<>(this, CatalogueHolder.class);
        manga_list.setAdapter(adapter);
    }

    public void initializeClickListener() {
        manga_list.setOnItemClickListener(
                (parent, view, position, id) ->
                        getPresenter().onMangaClick(position)
        );
    }

    public void initializeScrollListener() {
        scroll_listener = new EndlessScrollListener() {
            @Override
            public boolean onLoadMore(int page, int totalItemsCount) {
                getPresenter().loadMoreMangas(page);
                return true;
            }
        };

        manga_list.setOnScrollListener(scroll_listener);
    }

    public void resetScrollListener() {
        scroll_listener.resetScroll();
    }

    public int getScrollPage() {
        return scroll_listener.getCurrentPage();
    }

    public void setScrollPage(int page) {
        scroll_listener.setCurrentPage(page);
    }

    public void showProgressBar() {
        progress.setVisibility(ProgressBar.VISIBLE);
    }

    public void showGridProgressBar() {
        progress_grid.setVisibility(ProgressBar.VISIBLE);
    }

    public void hideProgressBar() {
        progress.setVisibility(ProgressBar.GONE);
        progress_grid.setVisibility(ProgressBar.GONE);
    }

    public ImageView getImageView(int position) {
        View v = manga_list.getChildAt(position -
                manga_list.getFirstVisiblePosition());

        if(v == null)
            return null;

        return (ImageView) v.findViewById(R.id.catalogue_thumbnail);
    }

    public void onMangasNext(List<Manga> newMangas) {
        adapter.addItems(newMangas);
    }
}
