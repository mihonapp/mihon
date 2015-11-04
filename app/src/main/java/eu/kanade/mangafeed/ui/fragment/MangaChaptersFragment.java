package eu.kanade.mangafeed.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.view.ActionMode;
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
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.services.DownloadService;
import eu.kanade.mangafeed.presenter.MangaChaptersPresenter;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.ui.activity.base.BaseActivity;
import eu.kanade.mangafeed.ui.adapter.ChaptersAdapter;
import eu.kanade.mangafeed.ui.fragment.base.BaseRxFragment;
import nucleus.factory.RequiresPresenter;
import rx.Observable;

@RequiresPresenter(MangaChaptersPresenter.class)
public class MangaChaptersFragment extends BaseRxFragment<MangaChaptersPresenter> implements
        ActionMode.Callback, ChaptersAdapter.OnItemClickListener {

    @Bind(R.id.chapter_list) RecyclerView chapters;
    @Bind(R.id.swipe_refresh) SwipeRefreshLayout swipeRefresh;

    private ChaptersAdapter adapter;

    private ActionMode actionMode;

    public static Fragment newInstance() {
        return new MangaChaptersFragment();
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_manga_chapters, container, false);
        ButterKnife.bind(this, view);

        chapters.setLayoutManager(new LinearLayoutManager(getActivity()));
        createAdapter();
        setSwipeRefreshListener();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!DownloadService.isRunning(getActivity())) {
            Intent intent = DownloadService.getStartIntent(getActivity());
            getActivity().startService(intent);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.chapters, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                getPresenter().refreshChapters();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createAdapter() {
        adapter = new ChaptersAdapter(this);
        chapters.setAdapter(adapter);
    }

    private void setSwipeRefreshListener() {
        swipeRefresh.setOnRefreshListener(() -> getPresenter().refreshChapters());
    }

    public void onNextChapters(List<Chapter> chapters) {
        adapter.setItems(chapters);
        closeActionMode();
    }

    public void onNextOnlineChapters() {
        swipeRefresh.setRefreshing(false);
    }

    public void setSwipeRefreshing() {
        swipeRefresh.setRefreshing(true);
    }

    public boolean isOnlineManga() {
        return ((MangaDetailActivity)getActivity()).isOnlineManga();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.chapter_selection, menu);
        adapter.setMode(ChaptersAdapter.MODE_MULTI);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_all:
                adapter.selectAll();
                return true;
            case R.id.action_mark_as_read:
                getPresenter().markChaptersRead(getSelectedChapters(), true);
                return true;
            case R.id.action_mark_as_unread:
                getPresenter().markChaptersRead(getSelectedChapters(), false);
                return true;
            case R.id.action_download:
                getPresenter().downloadChapters(getSelectedChapters());
                closeActionMode();
                return true;
            case R.id.action_delete:
                getPresenter().deleteChapters(getSelectedChapters());
                closeActionMode();
                return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        adapter.setMode(ChaptersAdapter.MODE_SINGLE);
        adapter.clearSelection();
        actionMode = null;
    }

    private Observable<Chapter> getSelectedChapters() {
        return Observable.from(adapter.getSelectedItems())
                .map(adapter::getItem);
    }

    public void closeActionMode() {
        if (actionMode != null)
            actionMode.finish();
    }

    @Override
    public boolean onListItemClick(int position) {
        if (actionMode != null && adapter.getMode() == ChaptersAdapter.MODE_MULTI) {
            toggleSelection(position);
            return true;
        } else {
            getPresenter().onChapterClicked(adapter.getItem(position));
            Intent intent = ReaderActivity.newInstance(getActivity());
            startActivity(intent);
            return false;
        }
    }

    @Override
    public void onListItemLongClick(int position) {
        if (actionMode == null)
            actionMode = ((BaseActivity)getActivity()).startSupportActionMode(this);

        toggleSelection(position);
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position, false);

        int count = adapter.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            setContextTitle(count);
            actionMode.invalidate();
        }
    }

    private void setContextTitle(int count) {
        actionMode.setTitle(getString(R.string.selected_chapters_title, count));
    }

}
