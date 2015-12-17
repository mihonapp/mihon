package eu.kanade.mangafeed.ui.catalogue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;

import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import eu.kanade.mangafeed.ui.main.MainActivity;
import eu.kanade.mangafeed.ui.manga.MangaActivity;
import eu.kanade.mangafeed.util.PageBundle;
import eu.kanade.mangafeed.util.ToastUtil;
import eu.kanade.mangafeed.widget.EndlessScrollListener;
import icepick.Icepick;
import icepick.State;
import nucleus.factory.RequiresPresenter;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;

@RequiresPresenter(CataloguePresenter.class)
public class CatalogueFragment extends BaseRxFragment<CataloguePresenter> {

    @Bind(R.id.gridView) GridView gridView;
    @Bind(R.id.progress) ProgressBar progress;
    @Bind(R.id.progress_grid) ProgressBar progressGrid;

    private Toolbar toolbar;
    private Spinner spinner;
    private CatalogueAdapter adapter;
    private EndlessScrollListener scrollListener;

    @State String query = "";
    @State int selectedIndex = -1;
    private final int SEARCH_TIMEOUT = 1000;

    private PublishSubject<String> queryDebouncerSubject;
    private Subscription queryDebouncerSubscription;

    public static CatalogueFragment newInstance() {
        return new CatalogueFragment();
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Icepick.restoreInstanceState(this, savedState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_catalogue, container, false);
        ButterKnife.bind(this, view);

        // Initialize adapter and scroll listener
        adapter = new CatalogueAdapter(this);
        scrollListener = new EndlessScrollListener(this::requestNextPage);
        gridView.setAdapter(adapter);
        gridView.setOnScrollListener(scrollListener);

        // Create toolbar spinner
        Context themedContext = getBaseActivity().getSupportActionBar() != null ?
                getBaseActivity().getSupportActionBar().getThemedContext() : getActivity();
        spinner = new Spinner(themedContext);
        CatalogueSpinnerAdapter spinnerAdapter = new CatalogueSpinnerAdapter(themedContext,
                android.R.layout.simple_spinner_item, getPresenter().getEnabledSources());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (savedState == null) selectedIndex = spinnerAdapter.getEmptyIndex();
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Source source = spinnerAdapter.getItem(position);
                // We add an empty source with id -1 that acts as a placeholder to show a hint
                // that asks to select a source
                if (source.getId() != -1 && (selectedIndex != position || adapter.isEmpty())) {
                    // Set previous selection if it's not a valid source and notify the user
                    if (!getPresenter().isValidSource(source)) {
                        spinner.setSelection(spinnerAdapter.getEmptyIndex());
                        ToastUtil.showShort(getActivity(), R.string.source_requires_login);
                    } else {
                        selectedIndex = position;
                        showProgressBar();
                        getPresenter().startRequesting(source);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        setToolbarTitle("");
        toolbar = ((MainActivity)getActivity()).getToolbar();
        toolbar.addView(spinner);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.catalogue_list, menu);

        // Initialize search menu
        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        if (!TextUtils.isEmpty(query)) {
            searchItem.expandActionView();
            searchView.setQuery(query, true);
            searchView.clearFocus();
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                onSearchEvent(query, true);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                onSearchEvent(newText, false);
                return true;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeSearchSubscription();
    }

    @Override
    public void onStop() {
        destroySearchSubscription();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        toolbar.removeView(spinner);
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Icepick.saveInstanceState(this, outState);
        super.onSaveInstanceState(outState);
    }

    private void initializeSearchSubscription() {
        queryDebouncerSubject = PublishSubject.create();
        queryDebouncerSubscription = queryDebouncerSubject
                .debounce(SEARCH_TIMEOUT, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::restartRequest);
    }

    private void destroySearchSubscription() {
        queryDebouncerSubscription.unsubscribe();
    }

    private void onSearchEvent(String query, boolean now) {
        // If the query is not debounced, resolve it instantly
        if (now)
            restartRequest(query);
        else if (queryDebouncerSubject != null)
            queryDebouncerSubject.onNext(query);
    }

    private void restartRequest(String newQuery) {
        // If text didn't change, do nothing
        if (query.equals(newQuery)) return;

        query = newQuery;
        showProgressBar();
        // Set adapter again for scrolling to top: http://stackoverflow.com/a/17577981/3263582
        gridView.setAdapter(adapter);
        gridView.setSelection(0);

        getPresenter().restartRequest(query);
    }

    private void requestNextPage() {
        if (getPresenter().hasNextPage()) {
            showGridProgressBar();
            getPresenter().requestNext();
        }
    }

    public void onAddPage(PageBundle<List<Manga>> page) {
        hideProgressBar();
        if (page.page == 0) {
            adapter.clear();
            scrollListener.resetScroll();
        }
        adapter.addAll(page.data);
    }

    public void onAddPageError() {
        hideProgressBar();
    }

    @OnItemClick(R.id.gridView)
    public void onMangaClick(int position) {
        Manga selectedManga = adapter.getItem(position);

        Intent intent = MangaActivity.newIntent(getActivity(), selectedManga);
        intent.putExtra(MangaActivity.MANGA_ONLINE, true);
        startActivity(intent);
    }

    public void updateImage(Manga manga) {
        ImageView imageView = getImageView(getMangaIndex(manga));
        if (imageView != null && manga.thumbnail_url != null) {
            getPresenter().coverCache.loadFromNetwork(imageView, manga.thumbnail_url,
                    getPresenter().getSource().getGlideHeaders());
        }
    }

    private ImageView getImageView(int position) {
        if (position == -1) return null;

        View v = gridView.getChildAt(position -
                gridView.getFirstVisiblePosition());

        if (v == null) return null;

        return (ImageView) v.findViewById(R.id.thumbnail);
    }

    private int getMangaIndex(Manga manga) {
        for (int i = adapter.getCount() - 1; i >= 0; i--) {
            if (manga.id.equals(adapter.getItem(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private void showProgressBar() {
        progress.setVisibility(ProgressBar.VISIBLE);
    }

    private void showGridProgressBar() {
        progressGrid.setVisibility(ProgressBar.VISIBLE);
    }

    private void hideProgressBar() {
        progress.setVisibility(ProgressBar.GONE);
        progressGrid.setVisibility(ProgressBar.GONE);
    }

}
