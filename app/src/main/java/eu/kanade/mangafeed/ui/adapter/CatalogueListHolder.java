package eu.kanade.mangafeed.ui.adapter;

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

@LayoutId(R.layout.item_catalogue)
public class CatalogueListHolder extends ItemViewHolder<Manga> {

    @ViewId(R.id.catalogue_title)
    TextView title;

    @ViewId(R.id.catalogue_thumbnail)
    ImageView image;

    public CatalogueListHolder(View view) {
        super(view);
    }

    @Override
    public void onSetValues(Manga manga, PositionInfo positionInfo) {
        title.setText(manga.title);

        String thumbnail;
        if (manga.thumbnail_url != null)
            thumbnail = manga.thumbnail_url;
        else
            thumbnail = "http://img1.wikia.nocookie.net/__cb20090524204255/starwars/images/thumb/1/1a/R2d2.jpg/400px-R2d2.jpg";

        Glide.with(getContext())
                .load(thumbnail)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .centerCrop()
                .into(image);
    }
}
