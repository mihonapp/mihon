package eu.kanade.mangafeed.ui.manga.chapter;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.download.DownloadService;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import eu.kanade.mangafeed.ui.decoration.DividerItemDecoration;
import eu.kanade.mangafeed.ui.manga.MangaActivity;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import nucleus.factory.RequiresPresenter;
import rx.Observable;

@RequiresPresenter(ChaptersPresenter.class)
public class ChaptersFragment extends BaseRxFragment<ChaptersPresenter> implements
        ActionMode.Callback, ChaptersAdapter.OnItemClickListener {

    @Bind(R.id.chapter_list) RecyclerView chapters;
    @Bind(R.id.swipe_refresh) SwipeRefreshLayout swipeRefresh;
    @Bind(R.id.toolbar_bottom) Toolbar toolbarBottom;

    private MenuItem sortUpBtn;
    private MenuItem sortDownBtn;
    private CheckBox readCb;

    private ChaptersAdapter adapter;

    private ActionMode actionMode;

    public static ChaptersFragment newInstance() {
        return new ChaptersFragment();
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
        chapters.addItemDecoration(new DividerItemDecoration(ContextCompat.getDrawable(this.getContext(), R.drawable.line_divider)));
        createAdapter();
        setSwipeRefreshListener();

        toolbarBottom.inflateMenu(R.menu.chapter_filter);

        sortUpBtn = toolbarBottom.getMenu().findItem(R.id.action_sort_up);
        sortDownBtn = toolbarBottom.getMenu().findItem(R.id.action_sort_down);
        readCb = (CheckBox) toolbarBottom.findViewById(R.id.action_show_unread);
        readCb.setOnCheckedChangeListener((arg, isCheked) -> getPresenter().setReadFilter(isCheked));
        toolbarBottom.setOnMenuItemClickListener(arg0 -> {
            switch (arg0.getItemId()) {
                case R.id.action_sort_up:
                case R.id.action_sort_down:
                    getPresenter().revertSortOrder();
                    return true;
            }
            return false;
        });
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.chapters, menu);
        super.onCreateOptionsMenu(menu, inflater);

        getPresenter().initSortIcon();
        getPresenter().initReadCb();
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
        return ((MangaActivity) getActivity()).isOnlineManga();
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
                return onSelectAll();
            case R.id.action_mark_as_read:
                return onMarkAsRead(getSelectedChapters());
            case R.id.action_mark_as_unread:
                return onMarkAsUnread(getSelectedChapters());
            case R.id.action_download:
                return onDownload(getSelectedChapters());
            case R.id.action_delete:
                return onDelete(getSelectedChapters());
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

    protected boolean onSelectAll() {
        adapter.selectAll();
        setContextTitle(adapter.getSelectedItemCount());
        actionMode.invalidate();
        return true;
    }

    protected boolean onMarkAsRead(Observable<Chapter> chapters) {
        getPresenter().markChaptersRead(chapters, true);
        return true;
    }

    protected boolean onMarkAsUnread(Observable<Chapter> chapters) {
        getPresenter().markChaptersRead(chapters, false);
        return true;
    }

    protected boolean onDownload(Observable<Chapter> chapters) {
        DownloadService.start(getActivity());
        getPresenter().downloadChapters(chapters);
        closeActionMode();
        return true;
    }

    protected boolean onDelete(Observable<Chapter> chapters) {
        getPresenter().deleteChapters(chapters);
        closeActionMode();
        return true;
    }

    @Override
    public boolean onListItemClick(int position) {
        if (actionMode != null && adapter.getMode() == ChaptersAdapter.MODE_MULTI) {
            toggleSelection(position);
            return true;
        } else {
            getPresenter().onChapterClicked(adapter.getItem(position));
            Intent intent = ReaderActivity.newIntent(getActivity());
            startActivity(intent);
            return false;
        }
    }

    @Override
    public void onListItemLongClick(int position) {
        if (actionMode == null)
            actionMode = ((BaseActivity) getActivity()).startSupportActionMode(this);

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

    public void setSortIcon(boolean aToZ) {
        if (sortUpBtn != null) sortUpBtn.setVisible(aToZ);
        if (sortDownBtn != null) sortDownBtn.setVisible(!aToZ);
    }

    public void setReadFilter(boolean onlyUnread) {
        if (readCb != null) readCb.setChecked(onlyUnread);
    }
}
