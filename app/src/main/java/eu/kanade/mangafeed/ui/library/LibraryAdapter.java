package eu.kanade.mangafeed.ui.library;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.List;

import eu.kanade.mangafeed.data.database.models.Manga;
import rx.Observable;
import uk.co.ribot.easyadapter.EasyAdapter;

public class LibraryAdapter extends EasyAdapter<Manga> implements Filterable {

    List<Manga> mangas;
    Filter filter;
    private LibraryPresenter presenter;

    public LibraryAdapter(LibraryFragment fragment) {
        super(fragment.getActivity(), LibraryHolder.class);
        filter = new LibraryFilter();
        presenter = fragment.getPresenter();
    }

    public void setNewItems(List<Manga> list) {
        super.setItems(list);
        mangas = list;
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    private class LibraryFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence charSequence) {
            FilterResults results = new FilterResults();
            String query = charSequence.toString().toLowerCase();

            if (query.length() == 0) {
                results.values = mangas;
                results.count = mangas.size();
            } else {
                List<Manga> filteredMangas = Observable.from(mangas)
                        .filter(x ->
                                (x.title != null && x.title.toLowerCase().contains(query)) ||
                                (x.author != null && x.author.toLowerCase().contains(query)) ||
                                (x.artist != null && x.artist.toLowerCase().contains(query)))
                        .toList()
                        .toBlocking()
                        .single();
                results.values = filteredMangas;
                results.count = filteredMangas.size();
            }

            return results;
        }

        @Override
        public void publishResults(CharSequence constraint, FilterResults results) {
            setItems((List<Manga>) results.values);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        LibraryHolder holder = (LibraryHolder) view.getTag();
        Manga manga = getItem(position);
        holder.loadCover(manga, presenter.sourceManager.get(manga.source), presenter.coverCache);
        return view;
    }
}
