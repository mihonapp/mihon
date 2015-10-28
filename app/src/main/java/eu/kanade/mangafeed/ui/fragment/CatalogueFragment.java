package eu.kanade.mangafeed.ui.fragment;

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

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.CataloguePresenter;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import eu.kanade.mangafeed.ui.holder.CatalogueHolder;
import eu.kanade.mangafeed.ui.fragment.base.BaseRxFragment;
import eu.kanade.mangafeed.util.PageBundle;
import eu.kanade.mangafeed.widget.EndlessScrollListener;
import nucleus.factory.RequiresPresenter;
import uk.co.ribot.easyadapter.EasyAdapter;

@RequiresPresenter(CataloguePresenter.class)
public class CatalogueFragment extends BaseRxFragment<CataloguePresenter> {

    @Bind(R.id.gridView)
    GridView manga_list;

    @Bind(R.id.progress)
    ProgressBar progress;

    @Bind(R.id.progress_grid)
    ProgressBar progress_grid;

    private EasyAdapter<Manga> adapter;
    private EndlessScrollListener scroll_listener;
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

    public EasyAdapter<Manga> getAdapter() {
        return adapter;
    }

    public void initializeAdapter() {
        adapter = new EasyAdapter<>(getActivity(), CatalogueHolder.class);
        manga_list.setAdapter(adapter);
    }

    @OnItemClick(R.id.gridView)
    public void onMangaClick(int position) {
        Manga selectedManga = adapter.getItem(position);

        Intent intent = MangaDetailActivity.newIntent(getActivity(), selectedManga);
        intent.putExtra(MangaDetailActivity.MANGA_ONLINE, true);
        startActivity(intent);
    }

    public void initializeScrollListener() {
        scroll_listener = new EndlessScrollListener(getPresenter()::requestNext);
        manga_list.setOnScrollListener(scroll_listener);
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

    public void onAddPage(PageBundle<List<Manga>> page) {
        if (page.page == 0) {
            adapter.getItems().clear();
            scroll_listener.resetScroll();
        }
        adapter.addItems(page.data);
    }

    private int getMangaIndex(Manga manga) {
        for (int i = 0; i < adapter.getCount(); i++) {
            if (manga.id == adapter.getItem(i).id) {
                return i;
            }
        }
        return -1;
    }

    private ImageView getImageView(int position) {
        View v = manga_list.getChildAt(position -
                manga_list.getFirstVisiblePosition());

        if(v == null)
            return null;

        return (ImageView) v.findViewById(R.id.catalogue_thumbnail);
    }

    public void updateImage(Manga manga) {
        ImageView imageView = getImageView(getMangaIndex(manga));
        if (imageView != null) {
            Glide.with(this)
                    .load(manga.thumbnail_url)
                    .centerCrop()
                    .into(imageView);
        }
    }

    public void restoreSearch(String mSearchName) {
        search = mSearchName;
    }
}
