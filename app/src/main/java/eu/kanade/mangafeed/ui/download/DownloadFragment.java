package eu.kanade.mangafeed.ui.download;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.download.model.Download;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(DownloadPresenter.class)
public class DownloadFragment extends BaseRxFragment<DownloadPresenter> {

    @Bind(R.id.download_list) RecyclerView recyclerView;
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

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setHasFixedSize(true);
        createAdapter();

        return view;
    }

    private void createAdapter() {
        adapter = new DownloadAdapter(getActivity());
        recyclerView.setAdapter(adapter);
    }

    public void onNextDownloads(List<Download> downloads) {
        adapter.setItems(downloads);
    }

    public void updateProgress(Download download) {
        DownloadHolder holder = getHolder(download);
        if (holder != null) {
            holder.setDownloadProgress(download);
        }
    }

    public void updateDownloadedPages(Download download) {
        DownloadHolder holder = getHolder(download);
        if (holder != null) {
            holder.setDownloadedPages(download);
        }
    }

    @Nullable
    private DownloadHolder getHolder(Download download) {
        return (DownloadHolder) recyclerView.findViewHolderForItemId(download.chapter.id);
    }

}
