package eu.kanade.tachiyomi.ui.recent;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.data.download.model.Download;
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder;
import eu.kanade.tachiyomi.util.ToastUtil;
import rx.Observable;

public class RecentChaptersHolder extends FlexibleViewHolder {

    /**
     * Adapter for recent chapters
     */
    private final RecentChaptersAdapter adapter;

    /**
     * Interface to global information about an application environment.
     */
    private Context context;

    /**
     * TextView containing chapter title
     */
    @Bind(R.id.chapter_title) TextView chapterTitle;

    /**
     * TextView containing manga name
     */
    @Bind(R.id.manga_title) TextView mangaTitle;

    /**
     * TextView containing download status
     */
    @Bind(R.id.download_text) TextView downloadText;

    /**
     * RelativeLayout containing popup menu with download options
     */
    @Bind(R.id.chapter_menu) RelativeLayout chapterMenu;

    /**
     * TextView containing read progress
     */
//    @Bind(R.id.chapter_pages) TextView pages;

    /**
     * Color of read chapter
     */
    private final int readColor;

    /**
     * Color of unread chapter
     */
    private final int unreadColor;

    /**
     * Object containing chapter information
     */
    private MangaChapter mangaChapter;

    /**
     * Constructor of RecentChaptersHolder
     *
     * @param view
     * @param adapter
     * @param onListItemClickListener
     */
    public RecentChaptersHolder(View view, RecentChaptersAdapter adapter, OnListItemClickListener onListItemClickListener) {
        super(view, adapter, onListItemClickListener);
        this.adapter = adapter;
        context = view.getContext();
        ButterKnife.bind(this, view);

        // Set colors.
        readColor = ContextCompat.getColor(view.getContext(), R.color.hint_text);
        unreadColor = ContextCompat.getColor(view.getContext(), R.color.primary_text);

        //Set OnClickListener for download menu
        chapterMenu.setOnClickListener(v -> v.post(() -> showPopupMenu(v)));
    }

    public void onSetValues(MangaChapter item) {
        this.mangaChapter = item;
        chapterTitle.setText(item.chapter.name);
        mangaTitle.setText(item.manga.title);

        if (item.chapter.read) {
            chapterTitle.setTextColor(readColor);
            mangaTitle.setTextColor(readColor);
        } else {
            chapterTitle.setTextColor(unreadColor);
            mangaTitle.setTextColor(unreadColor);

//            if (item.chapter.last_page_read > 0) {
//                pages.setText(context.getString(R.string.chapter_progress, item.chapter.last_page_read + 1));
//            } else {
//                pages.setText("");
//            }
        }

        onStatusChange(item.chapter.status);
    }

    public void onStatusChange(int status) {
        switch (status) {
            case Download.QUEUE:
                downloadText.setText(R.string.chapter_queued);
                break;
            case Download.DOWNLOADING:
                downloadText.setText(R.string.chapter_downloading);
                break;
            case Download.DOWNLOADED:
                downloadText.setText(R.string.chapter_downloaded);
                break;
            case Download.ERROR:
                downloadText.setText(R.string.chapter_error);
                break;
            default:
                downloadText.setText("");
                break;
        }
    }

    private void showPopupMenu(View view) {
        // Create a PopupMenu, giving it the clicked view for an anchor
        PopupMenu popup = new PopupMenu(adapter.getFragment().getActivity(), view);

        // Inflate our menu resource into the PopupMenu's Menu
        popup.getMenuInflater().inflate(R.menu.chapter_recent, popup.getMenu());

        // Hide download and show delete if the chapter is downloaded and
        if (mangaChapter.chapter.isDownloaded()) {
            Menu menu = popup.getMenu();
            menu.findItem(R.id.action_download).setVisible(false);
            menu.findItem(R.id.action_delete).setVisible(true);
        }

        // Hide mark as unread when the chapter is unread
        if (!mangaChapter.chapter.read /*&& mangaChapter.chapter.last_page_read == 0*/) {
            popup.getMenu().findItem(R.id.action_mark_as_unread).setVisible(false);
        }

        // Hide mark as read when the chapter is read
        if (mangaChapter.chapter.read) {
            popup.getMenu().findItem(R.id.action_mark_as_read).setVisible(false);
        }

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener(menuItem -> {
            Observable<Chapter> chapterObservable = Observable.just(mangaChapter.chapter);

            switch (menuItem.getItemId()) {
                case R.id.action_download:
                    return adapter.getFragment().onDownload(chapterObservable, mangaChapter.manga);
                case R.id.action_delete:
                    return adapter.getFragment().onDelete(chapterObservable, mangaChapter.manga);
                case R.id.action_mark_as_read:
                    ToastUtil.showShort(context, "Mark as read");
                    return adapter.getFragment().onMarkAsRead(chapterObservable);
                case R.id.action_mark_as_unread:
                    ToastUtil.showShort(context, "Mark as unread does not work, yet....");
                    return true;
//                    return adapter.getFragment().onMarkAsUnread(chapterObservable);
            }
            return false;
        });

        // Finally show the PopupMenu
        popup.show();
    }


}
