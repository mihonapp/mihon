package eu.kanade.mangafeed.ui.download;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Download;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;

@LayoutId(R.layout.item_download)
public class DownloadHolder extends ItemViewHolder<Download> {

    @ViewId(R.id.download_title) TextView downloadTitle;
    @ViewId(R.id.download_progress) ProgressBar downloadProgress;
    @ViewId(R.id.download_progress_text) TextView downloadProgressText;

    public DownloadHolder(View view) {
        super(view);
    }

    @Override
    public void onSetValues(Download download, PositionInfo positionInfo) {
        downloadTitle.setText(download.chapter.name);

        if (download.pages == null) {
            downloadProgress.setProgress(0);
            downloadProgress.setMax(1);
            downloadProgressText.setText("");
        } else {
            downloadProgress.setMax(download.pages.size() * 100);
            downloadProgress.setProgress(download.totalProgress);
            String progressText = download.downloadedImages + "/" + download.pages.size();
            downloadProgressText.setText(progressText);
        }
    }

}
