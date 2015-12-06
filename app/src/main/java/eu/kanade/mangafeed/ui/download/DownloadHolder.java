package eu.kanade.mangafeed.ui.download;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.download.model.Download;

public class DownloadHolder extends RecyclerView.ViewHolder {

    @Bind(R.id.download_title) TextView downloadTitle;
    @Bind(R.id.download_progress) ProgressBar downloadProgress;
    @Bind(R.id.download_progress_text) TextView downloadProgressText;

    public DownloadHolder(View view) {
        super(view);
        ButterKnife.bind(this, view);
    }

    public void onSetValues(Download download) {
        downloadTitle.setText(download.chapter.name);

        if (download.pages == null) {
            downloadProgress.setProgress(0);
            downloadProgress.setMax(1);
            downloadProgressText.setText("");
        } else {
            downloadProgress.setMax(download.pages.size() * 100);
            setDownloadProgress(download);
            setDownloadedPages(download);
        }
    }

    public void setDownloadedPages(Download download) {
        String progressText = download.downloadedImages + "/" + download.pages.size();
        downloadProgressText.setText(progressText);
    }

    public void setDownloadProgress(Download download) {
        if (downloadProgress.getMax() == 1)
            downloadProgress.setMax(download.pages.size() * 100);
        downloadProgress.setProgress(download.totalProgress);
    }

}
