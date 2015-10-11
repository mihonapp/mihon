package eu.kanade.mangafeed.view;

import android.content.Intent;

import eu.kanade.mangafeed.sources.Source;
import uk.co.ribot.easyadapter.EasyAdapter;

public interface CatalogueListView extends BaseView {
    Intent getIntent();
    void setSource(Source source);
    void setAdapter(EasyAdapter adapter);
}
