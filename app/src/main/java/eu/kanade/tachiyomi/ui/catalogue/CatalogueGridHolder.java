package eu.kanade.tachiyomi.ui.catalogue;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.iconics.view.IconicsImageView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Manga;

public class CatalogueGridHolder extends CatalogueHolder {

    @Bind(R.id.title) TextView title;
    @Bind(R.id.thumbnail) ImageView thumbnail;
    @Bind(R.id.favorite_sticker) IconicsImageView favoriteSticker;

    public CatalogueGridHolder(View view, CatalogueAdapter adapter, OnListItemClickListener listener) {
        super(view, adapter, listener);
        ButterKnife.bind(this, view);
    }

    @Override
    public void onSetValues(Manga manga, CataloguePresenter presenter) {
        title.setText(manga.title);
        // Set visibility of in library icon.
        favoriteSticker.setVisibility(manga.favorite ? View.VISIBLE : View.GONE);
        // Set alpha of thumbnail.
        thumbnail.setAlpha(manga.favorite ? 0.3f : 1.0f);
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