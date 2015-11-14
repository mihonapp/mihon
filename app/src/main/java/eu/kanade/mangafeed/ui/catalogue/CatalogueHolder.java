package eu.kanade.mangafeed.ui.catalogue;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Manga;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;

@LayoutId(R.layout.item_catalogue)
public class CatalogueHolder extends ItemViewHolder<Manga> {

    @ViewId(R.id.catalogue_title)
    TextView title;

    @ViewId(R.id.catalogue_thumbnail)
    ImageView image;

    public CatalogueHolder(View view) {
        super(view);
    }

    @Override
    public void onSetValues(Manga manga, PositionInfo positionInfo) {
        title.setText(manga.title);

        if (manga.thumbnail_url != null) {
            Glide.with(getContext())
                    .load(manga.thumbnail_url)
                    .diskCacheStrategy(DiskCacheStrategy.RESULT)
                    .centerCrop()
                    .into(image);
        } else {
            image.setImageResource(android.R.color.transparent);
        }
    }
}
