package eu.kanade.mangafeed.ui.adapter;

import android.view.View;
import android.widget.TextView;

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

    public CatalogueListHolder(View view) {
        super(view);
    }

    @Override
    public void onSetValues(Manga manga, PositionInfo positionInfo) {
        title.setText(manga.title);
    }
}
