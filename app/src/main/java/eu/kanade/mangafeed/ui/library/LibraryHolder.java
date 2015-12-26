package eu.kanade.mangafeed.ui.library;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.cache.CoverCache;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.ui.base.adapter.FlexibleViewHolder;

public class LibraryHolder extends FlexibleViewHolder {

    @Bind(R.id.thumbnail) ImageView thumbnail;
    @Bind(R.id.title) TextView title;
    @Bind(R.id.unreadText) TextView unreadText;

    public LibraryHolder(View view, FlexibleAdapter adapter, OnListItemClickListener listener) {
        super(view, adapter, listener);
        ButterKnife.bind(this, view);
    }

    public void onSetValues(Manga manga, LibraryPresenter presenter) {
        title.setText(manga.title);

        if (manga.unread > 0) {
            unreadText.setVisibility(View.VISIBLE);
            unreadText.setText(Integer.toString(manga.unread));
        } else {
            unreadText.setVisibility(View.GONE);
        }

        loadCover(manga, presenter.sourceManager.get(manga.source), presenter.coverCache);
    }

    private void loadCover(Manga manga, Source source, CoverCache coverCache) {
        if (manga.thumbnail_url != null) {
            coverCache.saveAndLoadFromCache(thumbnail, manga.thumbnail_url, source.getGlideHeaders());
        } else {
            thumbnail.setImageResource(android.R.color.transparent);
        }
    }

}
