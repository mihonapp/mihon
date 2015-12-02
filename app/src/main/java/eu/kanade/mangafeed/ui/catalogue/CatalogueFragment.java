package eu.kanade.mangafeed.ui.catalogue;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import eu.kanade.mangafeed.ui.manga.MangaActivity;
import eu.kanade.mangafeed.util.PageBundle;
import eu.kanade.mangafeed.widget.EndlessScrollListener;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(CataloguePresenter.class)
public class CatalogueFragment extends BaseRxFragment<CataloguePresenter> {

    @Bind(R.id.gridView) GridView gridView;
    @Bind(R.id.progress) ProgressBar progress;
    @Bind(R.id.progress_grid) ProgressBar progressGrid;

    private CatalogueAdapter adapter;
    private EndlessScrollListener scrollListener;
    private String search;

    public final static String SOURCE_ID = "source_id";

    public static CatalogueFragment newInstance(int source_id) {
        CatalogueFragment fragment = new CatalogueFragment();
        Bundle args = new Bundle();
        args.putInt(SOURCE_ID, source_id);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_catalogue, container, false);
        ButterKnife.bind(this, view);

        initializeAdapter();
        initializeScrollListener();

        int source_id = getArguments().getInt(SOURCE_ID, -1);

        showProgressBar();

        if (savedInstanceState == null)
            getPresenter().startRequesting(source_id);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.catalogue_list, menu);
        initializeSearch(menu);
    }

    private void initializeSearch(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView sv = (SearchView) searchItem.getActionView();
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                getPresenter().onSearchEvent(query, true);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                getPresenter().onSearchEvent(newText, false);
                return true;
            }
        });
        if (search != null && !search.equals("")) {
            searchItem.expandActionView();
            sv.setQuery(search, true);
            sv.clearFocus();
        }
    }

    public void initializeAdapter() {
        adapter = new CatalogueAdapter(this);
        gridView.setAdapter(adapter);
    }

    @OnItemClick(R.id.gridView)
    public void onMangaClick(int position) {
        Manga selectedManga = adapter.getItem(position);

        Intent intent = MangaActivity.newIntent(getActivity(), selectedManga);
        intent.putExtra(MangaActivity.MANGA_ONLINE, true);
        startActivity(intent);
    }

    public void initializeScrollListener() {
        scrollListener = new EndlessScrollListener(this::requestNext);
        gridView.setOnScrollListener(scrollListener);
    }

    public void requestNext() {
        if (getPresenter().requestNext())
            showGridProgressBar();
    }

    public void showProgressBar() {
        progress.setVisibility(ProgressBar.VISIBLE);
    }

    public void showGridProgressBar() {
        progressGrid.setVisibility(ProgressBar.VISIBLE);
    }

    public void hideProgressBar() {
        progress.setVisibility(ProgressBar.GONE);
        progressGrid.setVisibility(ProgressBar.GONE);
    }

    public void onAddPage(PageBundle<List<Manga>> page) {
        hideProgressBar();
        if (page.page == 0) {
            gridView.setSelection(0);
            adapter.clear();
            scrollListener.resetScroll();
        }
        adapter.addAll(page.data);
    }

    private int getMangaIndex(Manga manga) {
        for (int i = adapter.getCount() - 1; i >= 0; i--) {
            if (manga.id.equals(adapter.getItem(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private ImageView getImageView(int position) {
        if (position == -1)
            return null;

        View v = gridView.getChildAt(position -
                gridView.getFirstVisiblePosition());

        if(v == null)
            return null;

        return (ImageView) v.findViewById(R.id.thumbnail);
    }

    public void updateImage(Manga manga) {
        ImageView imageView = getImageView(getMangaIndex(manga));
        if (imageView != null) {
            GlideUrl url = new GlideUrl(manga.thumbnail_url,
                    getPresenter().getSource().getGlideHeaders());

            Glide.with(this)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .centerCrop()
                    .into(imageView);
        }
    }

    public void restoreSearch(String mSearchName) {
        search = mSearchName;
    }
}
