package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.LibraryPresenter;
import eu.kanade.mangafeed.ui.activity.MainActivity;
import eu.kanade.mangafeed.ui.adapter.CatalogueArrayAdapter;
import eu.kanade.mangafeed.ui.adapter.MangaLibraryHolder;
import eu.kanade.mangafeed.view.LibraryView;
import timber.log.Timber;
import uk.co.ribot.easyadapter.EasyAdapter;


public class LibraryFragment extends BaseFragment implements LibraryView {

    @Bind(R.id.gridView) GridView grid;
    LibraryPresenter presenter;
    CatalogueArrayAdapter<Manga> adapter;
    MainActivity activity;

    public static LibraryFragment newInstance() {
        LibraryFragment fragment = new LibraryFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        presenter = new LibraryPresenter(this);
        activity = (MainActivity)getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library, container, false);
        activity.setToolbarTitle(getString(R.string.library_title));
        ButterKnife.bind(this, view);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupToolbar();
        setMangaClickListener();
        presenter.initializeMangas();
        presenter.initializeSearch();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.library, menu);
        initializeSearch(menu);
    }

    private void initializeSearch(Menu menu) {
        final SearchView sv = (SearchView) menu.findItem(R.id.action_search).getActionView();
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                presenter.onQueryTextChange(newText);
                return true;
            }
        });
    }

    // LibraryView

    public void setMangas(List<Manga> mangas) {
        if (adapter == null) {
            adapter = new CatalogueArrayAdapter<>(
                    getActivity(),
                    MangaLibraryHolder.class,
                    mangas
            );
            grid.setAdapter(adapter);
        } else {
            adapter.setItems(mangas);
        }

    }

    private void setMangaClickListener() {
        grid.setOnItemClickListener(
                (parent, view, position, id) ->
                        presenter.onMangaClick(adapter, position)
        );
    }

    private void setupToolbar() {
        //activity.getSupportActionBar().
    }

    public CatalogueArrayAdapter getAdapter() {
        return adapter;
    }

}
