package eu.kanade.mangafeed.view;

import android.content.Intent;

import eu.kanade.mangafeed.sources.Source;

public interface CatalogueListView extends BaseView {
    Intent getIntent();
    void setSource(Source source);
}
