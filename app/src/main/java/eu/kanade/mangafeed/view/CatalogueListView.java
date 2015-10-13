package eu.kanade.mangafeed.view;

import android.content.Intent;
import android.widget.ImageView;

import uk.co.ribot.easyadapter.EasyAdapter;

public interface CatalogueListView extends BaseView {
    Intent getIntent();
    void setSourceTitle(String title);
    void setAdapter(EasyAdapter adapter);
    void setScrollListener();
    void resetScrollListener();
    ImageView getImageView(int position);
}
