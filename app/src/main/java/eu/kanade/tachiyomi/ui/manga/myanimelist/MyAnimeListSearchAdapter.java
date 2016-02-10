package eu.kanade.tachiyomi.ui.manga.myanimelist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.MangaSync;

public class MyAnimeListSearchAdapter extends ArrayAdapter<MangaSync> {

    public MyAnimeListSearchAdapter(Context context) {
        super(context, R.layout.dialog_myanimelist_search_item, new ArrayList<>());
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        // Get the data item for this position
        MangaSync sync = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        SearchViewHolder holder; // view lookup cache stored in tag
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            view = inflater.inflate(R.layout.dialog_myanimelist_search_item, parent, false);
            holder = new SearchViewHolder(view);
            view.setTag(holder);
        } else {
            holder = (SearchViewHolder) view.getTag();
        }
        holder.onSetValues(sync);
        return view;
    }

    public void setItems(List<MangaSync> syncs) {
        setNotifyOnChange(false);
        clear();
        addAll(syncs);
        notifyDataSetChanged();
    }

    public static class SearchViewHolder {

        @Bind(R.id.myanimelist_result_title) TextView title;

        public SearchViewHolder(View view) {
            ButterKnife.bind(this, view);
        }

        public void onSetValues(MangaSync sync) {
            title.setText(sync.title);
        }
    }
}
