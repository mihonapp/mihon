package eu.kanade.mangafeed.ui.adapter;

import android.view.View;
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

    public ChapterListHolder(View view) {
        super(view);
    }

    public void onSetValues(Chapter chapter, PositionInfo positionInfo) {
        title.setText(chapter.name);
    }
}
