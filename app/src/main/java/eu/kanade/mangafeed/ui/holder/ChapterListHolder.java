package eu.kanade.mangafeed.ui.holder;

import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Chapter;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;

@LayoutId(R.layout.item_chapter)
public class ChapterListHolder extends ItemViewHolder<Chapter> {

    @ViewId(R.id.chapter_title) TextView title;
    @ViewId(R.id.chapter_download_image) ImageView download_icon;
    @ViewId(R.id.chapter_pages) TextView pages;

    View view;

    public ChapterListHolder(View view) {
        super(view);
        this.view = view;
    }

    public void onSetValues(Chapter chapter, PositionInfo positionInfo) {
        title.setText(chapter.name);
        download_icon.setImageResource(R.drawable.ic_file_download_black_48dp);

        if (chapter.read) {
            title.setTextColor(ContextCompat.getColor(getContext(), R.color.chapter_read_text));
        } else {
            title.setTextColor(Color.BLACK);
        }

        if (chapter.last_page_read > 0 && !chapter.read) {
            pages.setText(getContext().getString(R.string.chapter_progress, chapter.last_page_read+1));
        } else {
            pages.setText("");
        }
    }

    @Override
    public void onSetListeners() {
        view.setOnClickListener(view -> {
            ChapterListener listener = getListener(ChapterListener.class);
            if (listener != null) {
                listener.onRowClicked(getItem());
            }
        });
    }

    public interface ChapterListener {
        void onRowClicked(Chapter chapter);
    }
}
