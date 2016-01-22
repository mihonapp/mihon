package eu.kanade.tachiyomi.ui.catalogue;

import android.view.View;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Manga;

public class CatalogueListHolder extends CatalogueHolder {

    @Bind(R.id.title) TextView title;

    public CatalogueListHolder(View view, CatalogueAdapter adapter, OnListItemClickListener listener) {
        super(view, adapter, listener);
        ButterKnife.bind(this, view);
    }

    @Override
    public void onSetValues(Manga manga, CataloguePresenter presenter) {
        title.setText(manga.title);
    }
}
