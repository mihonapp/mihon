package eu.kanade.mangafeed.ui.library;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.cache.CoverCache;
import eu.kanade.mangafeed.data.database.models.Manga;
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

        if (manga.thumbnail_url != null) {
            CoverCache.loadLocalInto(getContext(), thumbnail, manga.thumbnail_url);
        } else {
            thumbnail.setImageResource(android.R.color.transparent);
        }
    }

}
