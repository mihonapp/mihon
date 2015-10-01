package eu.kanade.mangafeed.ui.adapter;

import android.content.Context;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.models.Manga;
import rx.Observable;
import uk.co.ribot.easyadapter.EasyAdapter;

public class LibraryAdapter<T> extends EasyAdapter<T> implements Filterable {

    List<Manga> mangas;
    Filter filter;

    public LibraryAdapter(Context context) {
        super(context, MangaLibraryHolder.class);
        filter = new CatalogueFilter();
    }

    public void setNewItems(List<T> list) {
        super.setItems(list);
        mangas = (List<Manga>)list;
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    private class CatalogueFilter extends Filter {
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
                                x.title.toLowerCase().contains(query) ||
                                x.author.toLowerCase().contains(query) ||
                                x.artist.toLowerCase().contains(query))
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
            setItems((List<T>) results.values);
        }
    }


}
