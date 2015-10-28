package eu.kanade.mangafeed.ui.holder;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;


@LayoutId(R.layout.item_library)
public class LibraryHolder extends ItemViewHolder<Manga> {

    @ViewId(R.id.thumbnailImage)
    ImageView mThumbImage;

    @ViewId(R.id.titleText)
    TextView mTitleText;

    @ViewId(R.id.unreadText)
    TextView mUnreadText;

    public LibraryHolder(View view) {
        super(view);
    }

    public void onSetValues(Manga manga, PositionInfo positionInfo) {
        mTitleText.setText(manga.title);
        if (manga.unread > 0) {
            mUnreadText.setVisibility(View.VISIBLE);
            mUnreadText.setText(Integer.toString(manga.unread));
        }
        else {
            mUnreadText.setVisibility(View.GONE);
        }

        String thumbnail;
        if (manga.thumbnail_url != null)
            thumbnail = manga.thumbnail_url;
        else
            thumbnail = "http://img1.wikia.nocookie.net/__cb20090524204255/starwars/images/thumb/1/1a/R2d2.jpg/400px-R2d2.jpg";

        Glide.with(getContext())
                .load(thumbnail)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .centerCrop()
                .into(mThumbImage);
    }

}
