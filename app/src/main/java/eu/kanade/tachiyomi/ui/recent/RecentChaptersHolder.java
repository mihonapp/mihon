package eu.kanade.tachiyomi.ui.recent;

import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder;

public class RecentChaptersHolder extends FlexibleViewHolder {

    @Bind(R.id.chapter_title) TextView chapterTitle;
    @Bind(R.id.manga_title) TextView mangaTitle;

    private final int readColor;
    private final int unreadColor;

    public RecentChaptersHolder(View view, RecentChaptersAdapter adapter, OnListItemClickListener onListItemClickListener) {
        super(view, adapter, onListItemClickListener);
        ButterKnife.bind(this, view);

        readColor = ContextCompat.getColor(view.getContext(), R.color.hint_text);
        unreadColor = ContextCompat.getColor(view.getContext(), R.color.primary_text);
    }

    public void onSetValues(MangaChapter item) {
        chapterTitle.setText(item.chapter.name);
        mangaTitle.setText(item.manga.title);

        if (item.chapter.read) {
            chapterTitle.setTextColor(readColor);
            mangaTitle.setTextColor(readColor);
        } else {
            chapterTitle.setTextColor(unreadColor);
            mangaTitle.setTextColor(unreadColor);
        }
    }

}
