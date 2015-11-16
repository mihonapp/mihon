package eu.kanade.mangafeed.ui.download;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.download.model.Download;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(DownloadPresenter.class)
public class DownloadFragment extends BaseRxFragment<DownloadPresenter> {

    @Bind(R.id.download_list) RecyclerView downloadList;
    private LinearLayoutManager downloadListLayout;
    private DownloadAdapter adapter;

    public static DownloadFragment newInstance() {
        return new DownloadFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_download_queue, container, false);
        ButterKnife.bind(this, view);

        setToolbarTitle(R.string.label_download_queue);

        downloadListLayout = new LinearLayoutManager(getActivity());
        downloadList.setLayoutManager(downloadListLayout);
        createAdapter();

        return view;
    }

    private void createAdapter() {
        adapter = new DownloadAdapter(getActivity());
        downloadList.setAdapter(adapter);
    }

    public void onNextDownloads(List<Download> downloads) {
        adapter.setItems(downloads);
    }

    private View getDownloadRow(Download download) {
        int first = downloadListLayout.findFirstVisibleItemPosition();
        int last = downloadListLayout.findLastVisibleItemPosition();
        int pos = adapter.getPositionForItem(download);

        if (pos != -1 && pos >= first && pos <= last) {
            return downloadListLayout.getChildAt(pos - first);
        }
        return null;
    }

    public void updateProgress(Download download) {
        View row = getDownloadRow(download);
        if (row != null) {
            ProgressBar progress = (ProgressBar) row.findViewById(R.id.download_progress);
            if (progress.getMax() == 1) progress.setMax(download.pages.size() * 100);
            progress.setProgress(download.totalProgress);
        }
    }

    public void updateDownloadedPages(Download download) {
        View row = getDownloadRow(download);
        if (row != null) {
            TextView progress = (TextView) row.findViewById(R.id.download_progress_text);
            String progressText = download.downloadedImages + "/" + download.pages.size();
            progress.setText(progressText);
        }
    }
}
