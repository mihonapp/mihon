package eu.kanade.mangafeed.view;

import uk.co.ribot.easyadapter.EasyAdapter;

/**
 * Created by len on 10/10/2015.
 */
public interface CatalogueView extends BaseView {
    void setAdapter(EasyAdapter adapter);
    void setSourceClickListener();
}
