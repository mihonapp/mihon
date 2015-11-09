package eu.kanade.mangafeed.ui.adapter;

import android.content.Context;

import eu.kanade.mangafeed.data.models.Download;
import eu.kanade.mangafeed.ui.holder.DownloadHolder;
import uk.co.ribot.easyadapter.EasyRecyclerAdapter;

public class DownloadAdapter extends EasyRecyclerAdapter<Download> {

    public DownloadAdapter(Context context) {
        super(context, DownloadHolder.class);
    }

    public int getPositionForItem(Download item) {
        return getItems() != null && getItems().size() > 0 ? getItems().indexOf(item) : -1;
    }
}
