package eu.kanade.mangafeed.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.event.LibraryMangasEvent;
import eu.kanade.mangafeed.ui.base.fragment.BaseFragment;
import eu.kanade.mangafeed.ui.main.MainActivity;
import eu.kanade.mangafeed.ui.manga.MangaActivity;
import eu.kanade.mangafeed.util.EventBusHook;
import icepick.Icepick;
import icepick.State;

public class LibraryCategoryFragment extends BaseFragment {

    @Bind(R.id.gridView) GridView grid;

    protected LibraryCategoryAdapter adapter;
    @State Category category;

    public static LibraryCategoryFragment newInstance(Category category) {
        LibraryCategoryFragment fragment = new LibraryCategoryFragment();
        fragment.category = category;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library_category, container, false);
        ButterKnife.bind(this, view);
        Icepick.restoreInstanceState(this, savedState);

        adapter = new LibraryCategoryAdapter((MainActivity) getActivity());
        grid.setAdapter(adapter);

        return view;
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

    @OnItemClick(R.id.gridView)
    protected void onMangaClick(int position) {
        Intent intent = MangaActivity.newIntent(
                getActivity(),
                adapter.getItem(position)
        );
        getActivity().startActivity(intent);
    }

    public void setMangas(List<Manga> mangas) {
        if (mangas != null) {
            adapter.setNewItems(mangas);
        } else {
            adapter.getItems().clear();
        }
    }

}
