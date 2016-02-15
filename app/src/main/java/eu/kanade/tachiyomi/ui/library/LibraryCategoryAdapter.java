package eu.kanade.tachiyomi.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Manga;

public class LibraryCategoryAdapter extends FlexibleAdapter<LibraryHolder, Manga> {

    private List<Manga> mangas;
    private LibraryCategoryFragment fragment;

    public LibraryCategoryAdapter(LibraryCategoryFragment fragment) {
        this.fragment = fragment;
        mItems = new ArrayList<>();
        setHasStableIds(true);
    }

    public void setItems(List<Manga> list) {
        mItems = list;

        // A copy of manga that it's always unfiltered
        mangas = new ArrayList<>(list);
        updateDataSet(null);
    }

    public void clear() {
        mItems.clear();
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).id;
    }

    @Override
    public void updateDataSet(String param) {
        if (mangas != null) {
            filterItems(mangas);
            notifyDataSetChanged();
        }
    }

    @Override
    protected boolean filterObject(Manga manga, String query) {
        return (manga.title != null && manga.title.toLowerCase().contains(query)) ||
                (manga.author != null && manga.author.toLowerCase().contains(query));
    }

    @Override
    public LibraryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(fragment.getActivity()).inflate(R.layout.item_catalogue_grid, parent, false);
        return new LibraryHolder(v, this, fragment);
    }

    @Override
    public void onBindViewHolder(LibraryHolder holder, int position) {
        final LibraryPresenter presenter = ((LibraryFragment) fragment.getParentFragment()).getPresenter();
        final Manga manga = getItem(position);
        holder.onSetValues(manga, presenter);
        //When user scrolls this bind the correct selection status
        holder.itemView.setActivated(isSelected(position));
    }

    public int getCoverHeight() {
        return fragment.recycler.getItemWidth() / 3 * 4;
    }

}
