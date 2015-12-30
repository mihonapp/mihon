package eu.kanade.mangafeed.ui.library;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.f2prateek.rx.preferences.Preference;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.event.LibraryMangasEvent;
import eu.kanade.mangafeed.ui.base.adapter.FlexibleViewHolder;
import eu.kanade.mangafeed.ui.base.fragment.BaseFragment;
import eu.kanade.mangafeed.ui.manga.MangaActivity;
import eu.kanade.mangafeed.util.EventBusHook;
import eu.kanade.mangafeed.widget.AutofitRecyclerView;
import icepick.State;
import rx.Subscription;

public class LibraryCategoryFragment extends BaseFragment
        implements FlexibleViewHolder.OnListItemClickListener {

    @Bind(R.id.library_mangas) AutofitRecyclerView recycler;

    @State int position;
    private LibraryCategoryAdapter adapter;

    private Subscription numColumnsSubscription;

    public static LibraryCategoryFragment newInstance(int position) {
        LibraryCategoryFragment fragment = new LibraryCategoryFragment();
        fragment.position = position;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library_category, container, false);
        ButterKnife.bind(this, view);

        adapter = new LibraryCategoryAdapter(this);
        recycler.setHasFixedSize(true);
        recycler.setAdapter(adapter);

        if (getLibraryFragment().getActionMode() != null) {
            setMode(FlexibleAdapter.MODE_MULTI);
        }

        Preference<Integer> columnsPref = getResources().getConfiguration()
                .orientation == Configuration.ORIENTATION_PORTRAIT ?
                getLibraryPresenter().preferences.portraitColumns() :
                getLibraryPresenter().preferences.landscapeColumns();

        numColumnsSubscription = columnsPref.asObservable()
                .subscribe(recycler::setSpanCount);

        if (savedState != null) {
            adapter.onRestoreInstanceState(savedState);

            if (adapter.getMode() == FlexibleAdapter.MODE_SINGLE) {
                adapter.clearSelection();
            }
        }

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
        adapter.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @EventBusHook
    public void onEventMainThread(LibraryMangasEvent event) {
        List<Category> categories = getLibraryFragment().getAdapter().categories;
        // When a category is deleted, the index can be greater than the number of categories
        if (position >= categories.size())
            return;

        Category category = categories.get(position);
        List<Manga> mangas = event.getMangasForCategory(category);
        if (mangas == null) {
            mangas = new ArrayList<>();
        }
        setMangas(mangas);
    }

    protected void openManga(Manga manga) {
        Intent intent = MangaActivity.newIntent(getActivity(), manga);
        startActivity(intent);
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
        if (getLibraryFragment().getActionMode() != null && position != -1) {
            toggleSelection(position);
            return true;
        } else {
            openManga(adapter.getItem(position));
            return false;
        }
    }

    @Override
    public void onListItemLongClick(int position) {
        getLibraryFragment().createActionModeIfNeeded();
        toggleSelection(position);
    }

    private void toggleSelection(int position) {
        LibraryFragment f = getLibraryFragment();

        adapter.toggleSelection(position, false);
        f.getPresenter().setSelection(adapter.getItem(position), adapter.isSelected(position));

        int count = f.getPresenter().selectedMangas.size();
        if (count == 0) {
            f.destroyActionModeIfNeeded();
        } else {
            f.setContextTitle(count);
            f.invalidateActionMode();
        }
    }

    public void setMode(int mode) {
        adapter.setMode(mode);
        if (mode == FlexibleAdapter.MODE_SINGLE) {
            adapter.clearSelection();
        }
    }

    private LibraryFragment getLibraryFragment() {
        return (LibraryFragment) getParentFragment();
    }

    private LibraryPresenter getLibraryPresenter() {
        return getLibraryFragment().getPresenter();
    }
}
