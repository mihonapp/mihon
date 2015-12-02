package eu.kanade.mangafeed.ui.manga.chapter;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.download.model.Download;
import rx.Observable;

public class ChaptersHolder extends RecyclerView.ViewHolder implements
        View.OnClickListener, View.OnLongClickListener {

    private ChaptersAdapter adapter;
    private Chapter item;

    @Bind(R.id.chapter_title) TextView title;
    @Bind(R.id.download_text) TextView downloadText;
    @Bind(R.id.chapter_menu) RelativeLayout chapterMenu;
    @Bind(R.id.chapter_pages) TextView pages;
    @Bind(R.id.chapter_date) TextView date;

    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

    public ChaptersHolder(View view) {
        super(view);
        ButterKnife.bind(this, view);
    }

    public ChaptersHolder(View view, final ChaptersAdapter adapter) {
        this(view);

        this.adapter = adapter;
        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);
        chapterMenu.setOnClickListener(v -> v.post(() -> showPopupMenu(v)));
    }

    public void onSetValues(Context context, Chapter chapter) {
        this.item = chapter;
        title.setText(chapter.name);

        if (chapter.read) {
            title.setTextColor(ContextCompat.getColor(context, R.color.hint_text));
        } else {
            title.setTextColor(ContextCompat.getColor(context, R.color.primary_text));
        }

        if (!chapter.read && chapter.last_page_read > 0) {
            pages.setText(context.getString(R.string.chapter_progress, chapter.last_page_read + 1));
        } else {
            pages.setText("");
        }

        switch (chapter.status) {
            case Download.QUEUE:
                downloadText.setText(R.string.chapter_queued); break;
            case Download.DOWNLOADING:
                downloadText.setText(R.string.chapter_downloading); break;
            case Download.DOWNLOADED:
                downloadText.setText(R.string.chapter_downloaded); break;
            case Download.ERROR:
                downloadText.setText(R.string.chapter_error); break;
            default:
                downloadText.setText(""); break;
        }

        date.setText(sdf.format(new Date(chapter.date_upload)));
        toggleActivation();
    }

    private void toggleActivation() {
        itemView.setActivated(adapter.isSelected(getAdapterPosition()));
    }

    @Override
    public void onClick(View v) {
        if (adapter.clickListener.onListItemClick(getAdapterPosition()))
            toggleActivation();
    }

    @Override
    public boolean onLongClick(View v) {
        adapter.clickListener.onListItemLongClick(getAdapterPosition());
        toggleActivation();
        return true;
    }


    private void showPopupMenu(View view) {
        // Create a PopupMenu, giving it the clicked view for an anchor
        PopupMenu popup = new PopupMenu(adapter.getChaptersFragment().getActivity(), view);

        // Inflate our menu resource into the PopupMenu's Menu
        popup.getMenuInflater().inflate(R.menu.chapter_single, popup.getMenu());

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener(menuItem -> {
            Observable<Chapter> chapter = Observable.just(item);

            switch (menuItem.getItemId()) {
                case R.id.action_mark_as_read:
                    return adapter.getChaptersFragment().onMarkAsRead(chapter);
                case R.id.action_mark_as_unread:
                    return adapter.getChaptersFragment().onMarkAsUnread(chapter);
                case R.id.action_download:
                    return adapter.getChaptersFragment().onDownload(chapter);
                case R.id.action_delete:
                    return adapter.getChaptersFragment().onDelete(chapter);
            }
            return false;
        });

        // Finally show the PopupMenu
        popup.show();
    }

}
