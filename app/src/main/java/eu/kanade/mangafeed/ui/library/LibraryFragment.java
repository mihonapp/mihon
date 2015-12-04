package eu.kanade.mangafeed.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.sync.LibraryUpdateService;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import eu.kanade.mangafeed.ui.manga.MangaActivity;
import nucleus.factory.RequiresPresenter;
import rx.Observable;

@RequiresPresenter(LibraryPresenter.class)
public class LibraryFragment extends BaseRxFragment<LibraryPresenter> {

    @Bind(R.id.gridView) GridView grid;
    private LibraryAdapter adapter;

    public static LibraryFragment newInstance() {
        return new LibraryFragment();
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
        View view = inflater.inflate(R.layout.fragment_library, container, false);
        setToolbarTitle(getString(R.string.label_library));
        ButterKnife.bind(this, view);

        createAdapter();
        setMangaLongClickListener();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.library, menu);
        initializeSearch(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if (!LibraryUpdateService.isRunning(getActivity())) {
                    Intent intent = LibraryUpdateService.getStartIntent(getActivity());
                    getActivity().startService(intent);
                }

                return true;
        }

        return super.onOptionsItemSelected(item);
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
                adapter.getFilter().filter(newText);
                return true;
            }
        });
    }

    private void createAdapter() {
        adapter = new LibraryAdapter(this);
        grid.setAdapter(adapter);
    }

    public void onNextMangas(List<Manga> mangas) {
        adapter.setNewItems(mangas);
    }

    @OnItemClick(R.id.gridView)
    protected void onMangaClick(int position) {
        Intent intent = MangaActivity.newIntent(
                getActivity(),
                adapter.getItem(position)
        );
        getActivity().startActivity(intent);
    }

    private void setMangaLongClickListener() {
        grid.setMultiChoiceModeListener(new GridView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                mode.setTitle(getResources().getString(R.string.library_selection_title)
                        + ": " + grid.getCheckedItemCount());
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.library_selection, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_delete:
                        getPresenter().deleteMangas(getSelectedMangas());
                        mode.finish();
                        return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

            }
        });
    }

    private Observable<Manga> getSelectedMangas() {
        SparseBooleanArray checkedItems = grid.getCheckedItemPositions();
        return Observable.range(0, checkedItems.size())
                .map(checkedItems::keyAt)
                .map(adapter::getItem);
    }

}
