package eu.kanade.mangafeed.view;

import android.content.Intent;
import android.widget.ImageView;

import uk.co.ribot.easyadapter.EasyAdapter;

public interface CatalogueView extends BaseView {
    Intent getIntent();
    void setTitle(String title);
    void setAdapter(EasyAdapter adapter);
    void setMangaClickListener();
    void setScrollListener();
    void resetScrollListener();
    void showProgressBar();
    void showGridProgressBar();
    void hideProgressBar();
    ImageView getImageView(int position);
}
