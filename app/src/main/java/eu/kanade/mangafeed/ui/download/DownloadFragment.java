package eu.kanade.mangafeed.ui.download;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.download.DownloadService;
import eu.kanade.mangafeed.data.download.model.Download;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import nucleus.factory.RequiresPresenter;
import rx.Subscription;

@RequiresPresenter(DownloadPresenter.class)
public class DownloadFragment extends BaseRxFragment<DownloadPresenter> {

    @Bind(R.id.download_list) RecyclerView recyclerView;
    private DownloadAdapter adapter;

    private MenuItem startButton;
    private MenuItem stopButton;

    private Subscription queueStatusSubscription;
    private boolean isRunning;

    public static DownloadFragment newInstance() {
        return new DownloadFragment();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setHasOptionsMenu(true);
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.download_queue, menu);
        startButton = menu.findItem(R.id.start_queue);
        stopButton = menu.findItem(R.id.stop_queue);

        // Menu seems to be inflated after onResume in fragments, so we initialize them here
        startButton.setVisible(!isRunning && !getPresenter().downloadManager.getQueue().isEmpty());
        stopButton.setVisible(isRunning);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.start_queue:
                DownloadService.start(getActivity());
                break;
            case R.id.stop_queue:
                DownloadService.stop(getActivity());
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        queueStatusSubscription = getPresenter().downloadManager.getRunningSubject()
                .subscribe(this::onRunningChange);
    }

    @Override
    public void onPause() {
        queueStatusSubscription.unsubscribe();
        super.onPause();
    }

    private void onRunningChange(boolean running) {
        isRunning = running;
        if (startButton != null)
            startButton.setVisible(!running && !getPresenter().downloadManager.getQueue().isEmpty());
        if (stopButton != null)
            stopButton.setVisible(running);
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
