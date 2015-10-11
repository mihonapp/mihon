package eu.kanade.mangafeed.presenter;

import android.content.Intent;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.view.CatalogueListView;

public class CatalogueListPresenter {

    CatalogueListView view;

    @Inject SourceManager sourceManager;

    public CatalogueListPresenter(CatalogueListView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void initializeSource() {
        Intent intent = view.getIntent();
        int sourceId = intent.getIntExtra(Intent.EXTRA_UID, -1);
        view.setSource(sourceManager.get(sourceId));
    }
}
