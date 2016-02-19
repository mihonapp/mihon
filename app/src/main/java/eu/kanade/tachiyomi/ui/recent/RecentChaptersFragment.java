package eu.kanade.tachiyomi.ui.recent;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.data.download.DownloadService;
import eu.kanade.tachiyomi.data.download.model.Download;
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder;
import eu.kanade.tachiyomi.ui.base.fragment.BaseRxFragment;
import eu.kanade.tachiyomi.ui.decoration.DividerItemDecoration;
import eu.kanade.tachiyomi.ui.reader.ReaderActivity;
import nucleus.factory.RequiresPresenter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Fragment that shows recent chapters.
 * Uses R.layout.fragment_recent_chapters.
 * UI related actions should be called from here.
 */
@RequiresPresenter(RecentChaptersPresenter.class)
public class RecentChaptersFragment extends BaseRxFragment<RecentChaptersPresenter> implements FlexibleViewHolder.OnListItemClickListener {

    @Bind(R.id.chapter_list) RecyclerView recyclerView;

    private RecentChaptersAdapter adapter;

    public static RecentChaptersFragment newInstance() {
        return new RecentChaptersFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_recent_chapters, container, false);
        ButterKnife.bind(this, view);

        // Init RecyclerView and adapter
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.addItemDecoration(new DividerItemDecoration(ContextCompat.getDrawable(
                getContext(), R.drawable.line_divider)));
        recyclerView.setHasFixedSize(true);
        adapter = new RecentChaptersAdapter(this);
        recyclerView.setAdapter(adapter);

        setToolbarTitle(R.string.label_recent_updates);
        return view;
    }

    /**
     * Populate adapter with chapters
     *
     * @param chapters list of chapters
     */
    public void onNextMangaChapters(List<Object> chapters) {
        adapter.setItems(chapters);
    }

    @Override
    public boolean onListItemClick(int position) {
        // Get item from position
        Object item = adapter.getItem(position);
        if (item instanceof MangaChapter) {
            // Open chapter in reader
            openChapter((MangaChapter) item);
        }
        return false;
    }

    @Override
    public void onListItemLongClick(int position) {
        // Empty function
    }

    /**
     * Open chapter in reader
     *
     * @param chapter selected chapter
     */
    private void openChapter(MangaChapter chapter) {
        getPresenter().onOpenChapter(chapter);
        Intent intent = ReaderActivity.newIntent(getActivity());
        startActivity(intent);
    }

    /**
     * Update download status of chapter
     *
     * @param download download object containing download progress.
     */
    public void onChapterStatusChange(Download download) {
        RecentChaptersHolder holder = getHolder(download.chapter);
        if (holder != null)
            holder.onStatusChange(download.getStatus());
    }

    @Nullable
    private RecentChaptersHolder getHolder(Chapter chapter) {
        return (RecentChaptersHolder) recyclerView.findViewHolderForItemId(chapter.id);
    }

    /**
     * Start downloading chapter
     *
     * @param chapters selected chapters
     * @param manga    manga that belongs to chapter
     * @return true
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean onDownload(Observable<Chapter> chapters, Manga manga) {
        // Start the download service.
        DownloadService.start(getActivity());

        // Refresh data on download competition.
        Observable<Chapter> observable = chapters
                .doOnCompleted(adapter::notifyDataSetChanged);

        // Download chapter.
        getPresenter().downloadChapter(observable, manga);
        return true;
    }

    /**
     * Start deleting chapter
     * @param chapters selected chapters
     * @param manga manga that belongs to chapter
     * @return success of deletion.
     */
    protected boolean onDelete(Observable<Chapter> chapters, Manga manga) {
        int size = adapter.getSelectedItemCount();

        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .title(R.string.deleting)
                .progress(false, size, true)
                .cancelable(false)
                .show();

        Observable<Chapter> observable = chapters
                .concatMap(chapter -> {
                    getPresenter().deleteChapter(chapter, manga);
                    return Observable.just(chapter);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(chapter -> {
                    dialog.incrementProgress(1);
                    chapter.status = Download.NOT_DOWNLOADED;
                })
                .doOnCompleted(adapter::notifyDataSetChanged)
                .finallyDo(dialog::dismiss);

        getPresenter().deleteChapters(observable);

        return true;
    }

    /**
     * Mark chapter as read
     *
     * @param chapters selected chapter
     * @return true
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean onMarkAsRead(Observable<Chapter> chapters) {
        getPresenter().markChaptersRead(chapters, true);
        return true;
    }

    /**
     * Mark chapter as unread
     *
     * @param chapters selected chapter
     * @return true
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean onMarkAsUnread(Observable<Chapter> chapters) {
        getPresenter().markChaptersRead(chapters, false);
        return true;
    }


}
