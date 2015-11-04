package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Download;
import eu.kanade.mangafeed.presenter.DownloadQueuePresenter;
import eu.kanade.mangafeed.ui.fragment.base.BaseRxFragment;
import eu.kanade.mangafeed.ui.holder.DownloadHolder;
import nucleus.factory.RequiresPresenter;
import uk.co.ribot.easyadapter.EasyRecyclerAdapter;

@RequiresPresenter(DownloadQueuePresenter.class)
public class DownloadQueueFragment extends BaseRxFragment<DownloadQueuePresenter> {

    @Bind(R.id.download_list) RecyclerView downloadList;
    private EasyRecyclerAdapter<Download> adapter;

    public static DownloadQueueFragment newInstance() {
        return new DownloadQueueFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_download_queue, container, false);
        ButterKnife.bind(this, view);

        setToolbarTitle(R.string.download_title);

        downloadList.setLayoutManager(new LinearLayoutManager(getActivity()));
        createAdapter();

        return view;
    }

    private void createAdapter() {
        adapter = new EasyRecyclerAdapter<>(getActivity(), DownloadHolder.class);
        downloadList.setAdapter(adapter);
    }

    public void onNextDownloads(List<Download> downloads) {
        adapter.setItems(downloads);
    }

    // TODO use a better approach
    public void updateProgress(Download download) {
        for (int i = 0; i < adapter.getItems().size(); i++) {
            if (adapter.getItem(i) == download) {
                adapter.notifyItemChanged(i);
                break;
            }
        }
    }

}
