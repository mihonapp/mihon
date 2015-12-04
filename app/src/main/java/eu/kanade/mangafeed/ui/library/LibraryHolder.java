package eu.kanade.mangafeed.ui.library;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.cache.CoverCache;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.base.Source;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;


@LayoutId(R.layout.item_catalogue)
public class LibraryHolder extends ItemViewHolder<Manga> {

    @ViewId(R.id.thumbnail) ImageView thumbnail;
    @ViewId(R.id.title) TextView title;
    @ViewId(R.id.author) TextView author;
    @ViewId(R.id.unreadText) TextView unreadText;

    public LibraryHolder(View view) {
        super(view);
    }

    public void onSetValues(Manga manga, PositionInfo positionInfo) {
        title.setText(manga.title);
        author.setText(manga.author);

        if (manga.unread > 0) {
            unreadText.setVisibility(View.VISIBLE);
            unreadText.setText(Integer.toString(manga.unread));
        } else {
            unreadText.setVisibility(View.GONE);
        }
    }

    public void loadCover(Manga manga, Source source, CoverCache coverCache) {
        if (manga.thumbnail_url != null) {
            coverCache.saveAndLoadFromCache(thumbnail, manga.thumbnail_url, source.getGlideHeaders());
        } else {
            thumbnail.setImageResource(android.R.color.transparent);
        }
    }

}
