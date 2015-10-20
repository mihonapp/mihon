package eu.kanade.mangafeed.ui.adapter;

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

    @ViewId(R.id.chapter_title)
    TextView title;

    @ViewId(R.id.chapter_download_image)
    ImageView download_icon;

    View view;

    public ChapterListHolder(View view) {
        super(view);
        this.view = view;
    }

    public void onSetValues(Chapter chapter, PositionInfo positionInfo) {
        title.setText(chapter.name);
        download_icon.setImageResource(R.drawable.ic_file_download_black_48dp);
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
