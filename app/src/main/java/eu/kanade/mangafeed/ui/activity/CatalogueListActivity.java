package eu.kanade.mangafeed.ui.activity;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.ListView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.presenter.CatalogueListPresenter;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.view.CatalogueListView;
import uk.co.ribot.easyadapter.EasyAdapter;

public class CatalogueListActivity extends BaseActivity implements CatalogueListView {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.catalogue_manga_list)
    ListView manga_list;

    private CatalogueListPresenter presenter;
    private Source source;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalogue_list);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        presenter = new CatalogueListPresenter(this);
        presenter.initializeSource();
    }

    public void setSource(Source source) {
        this.source = source;
        setToolbarTitle(source.getName());
    }

    public void setAdapter(EasyAdapter adapter) {
        manga_list.setAdapter(adapter);
    }

}
