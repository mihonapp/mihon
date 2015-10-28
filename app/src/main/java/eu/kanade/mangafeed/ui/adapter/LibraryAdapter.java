package eu.kanade.mangafeed.ui.adapter;

import android.content.Context;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.List;

import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.holder.LibraryHolder;
import rx.Observable;
import uk.co.ribot.easyadapter.EasyAdapter;

public class LibraryAdapter extends EasyAdapter<Manga> implements Filterable {

    List<Manga> mangas;
    Filter filter;

    public LibraryAdapter(Context context) {
        super(context, LibraryHolder.class);
        filter = new LibraryFilter();
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
            setItems((List<Manga >) results.values);
        }
    }


}
