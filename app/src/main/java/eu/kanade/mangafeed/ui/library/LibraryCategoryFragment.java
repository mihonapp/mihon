package eu.kanade.mangafeed.ui.library;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.f2prateek.rx.preferences.Preference;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.event.LibraryMangasEvent;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;
import eu.kanade.mangafeed.ui.base.adapter.FlexibleViewHolder;
import eu.kanade.mangafeed.ui.base.fragment.BaseFragment;
import eu.kanade.mangafeed.ui.manga.MangaActivity;
import eu.kanade.mangafeed.util.EventBusHook;
import eu.kanade.mangafeed.widget.AutofitRecyclerView;
import icepick.Icepick;
import icepick.State;
import rx.Subscription;

public class LibraryCategoryFragment extends BaseFragment implements
        ActionMode.Callback, FlexibleViewHolder.OnListItemClickListener {

    @Inject PreferencesHelper preferences;

    @Bind(R.id.library_mangas) AutofitRecyclerView recycler;

    @State Category category;
    private LibraryCategoryAdapter adapter;
    private ActionMode actionMode;

    private Subscription numColumnsSubscription;

    private static final int INVALID_POSITION = -1;

    public static LibraryCategoryFragment newInstance(Category category) {
        LibraryCategoryFragment fragment = new LibraryCategoryFragment();
        fragment.category = category;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get(getActivity()).getComponent().inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library_category, container, false);
        ButterKnife.bind(this, view);
        Icepick.restoreInstanceState(this, savedState);

        recycler.setHasFixedSize(true);

        adapter = new LibraryCategoryAdapter(this);
        recycler.setAdapter(adapter);

        Preference<Integer> columnsPref = getResources().getConfiguration()
                .orientation == Configuration.ORIENTATION_PORTRAIT ?
                preferences.portraitColumns() :
                preferences.landscapeColumns();

        numColumnsSubscription = columnsPref.asObservable()
                .subscribe(recycler::setSpanCount);

        return view;
    }

    @Override
    public void onDestroyView() {
        numColumnsSubscription.unsubscribe();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerForStickyEvents();
    }

    @Override
    public void onPause() {
        unregisterForEvents();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Icepick.saveInstanceState(this, outState);
        super.onSaveInstanceState(outState);
    }

    @EventBusHook
    public void onEventMainThread(LibraryMangasEvent event) {
        setMangas(event.getMangas().get(category.id));
    }

    protected void openManga(Manga manga) {
        Intent intent = MangaActivity.newIntent(getActivity(), manga);
        getActivity().startActivity(intent);
    }

    public void setMangas(List<Manga> mangas) {
        if (mangas != null) {
            adapter.setItems(mangas);
        } else {
            adapter.clear();
        }
    }

    @Override
    public boolean onListItemClick(int position) {
        if (actionMode != null && position != INVALID_POSITION) {
            toggleSelection(position);
            return true;
        } else {
            openManga(adapter.getItem(position));
            return false;
        }
    }

    @Override
    public void onListItemLongClick(int position) {
        if (actionMode == null)
            actionMode = ((BaseActivity) getActivity()).startSupportActionMode(this);

        toggleSelection(position);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.library_selection, menu);
        adapter.setMode(LibraryCategoryAdapter.MODE_MULTI);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        adapter.setMode(LibraryCategoryAdapter.MODE_SINGLE);
        adapter.clearSelection();
        actionMode = null;
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position, false);

        int count = adapter.getSelectedItemCount();
        if (count == 0) {
            actionMode.finish();
        } else {
            setContextTitle(count);
            actionMode.invalidate();
        }
    }

    private void setContextTitle(int count) {
        actionMode.setTitle(getString(R.string.selected_chapters_title, count));
    }
}
