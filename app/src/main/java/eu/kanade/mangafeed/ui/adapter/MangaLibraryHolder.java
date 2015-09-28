package eu.kanade.mangafeed.ui.adapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;


@LayoutId(R.layout.item_library)
public class MangaLibraryHolder extends ItemViewHolder<Manga> {

    @ViewId(R.id.thumbnailImageView)
    ImageView mImageView;

    @ViewId(R.id.nameTextView)
    TextView mTextView;

    public MangaLibraryHolder(View view) {
        super(view);
    }

    public void onSetValues(Manga manga, PositionInfo positionInfo) {
        mTextView.setText(manga.title);
        Glide.with(getContext())
                .load("http://img1.wikia.nocookie.net/__cb20090524204255/starwars/images/thumb/1/1a/R2d2.jpg/400px-R2d2.jpg")
                .centerCrop()
                .into(mImageView);
    }

}
