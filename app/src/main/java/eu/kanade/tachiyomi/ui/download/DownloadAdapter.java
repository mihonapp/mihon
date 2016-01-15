package eu.kanade.tachiyomi.ui.download;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.download.model.Download;

public class DownloadAdapter extends FlexibleAdapter<DownloadHolder, Download> {

    private Context context;

    public DownloadAdapter(Context context) {
        this.context = context;
        mItems = new ArrayList<>();
        setHasStableIds(true);
    }

    @Override
    public DownloadHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_download, parent, false);
        return new DownloadHolder(v);
    }

    @Override
    public void onBindViewHolder(DownloadHolder holder, int position) {
        final Download download = getItem(position);
        holder.onSetValues(download);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).chapter.id;
    }

    public void setItems(List<Download> downloads) {
        mItems = downloads;
        notifyDataSetChanged();
    }

    @Override
    public void updateDataSet(String param) {}

}
