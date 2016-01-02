package eu.kanade.mangafeed.ui.catalogue;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.ui.base.adapter.FlexibleViewHolder;

public class CatalogueHolder extends FlexibleViewHolder {

    @Bind(R.id.title) TextView title;
    @Bind(R.id.thumbnail) ImageView thumbnail;
    @Bind(R.id.favorite_sticker) ImageView favoriteSticker;

    public CatalogueHolder(View view, CatalogueAdapter adapter, OnListItemClickListener listener) {
        super(view, adapter, listener);
        ButterKnife.bind(this, view);
    }

    public void onSetValues(Manga manga, CataloguePresenter presenter) {
        title.setText(manga.title);
        favoriteSticker.setVisibility(manga.favorite ? View.VISIBLE : View.GONE);
        setImage(manga, presenter);
    }

    public void setImage(Manga manga, CataloguePresenter presenter) {
        if (manga.thumbnail_url != null) {
            presenter.coverCache.loadFromNetwork(thumbnail, manga.thumbnail_url,
                    presenter.getSource().getGlideHeaders());
        } else {
            thumbnail.setImageResource(android.R.color.transparent);
        }
    }
}