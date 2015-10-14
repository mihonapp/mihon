package eu.kanade.mangafeed.ui.activity;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.presenter.MangaCataloguePresenter;
import eu.kanade.mangafeed.view.MangaCatalogueView;

public class MangaCatalogueActivity extends BaseActivity implements MangaCatalogueView {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    private MangaCataloguePresenter presenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manga_catalogue);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        presenter = new MangaCataloguePresenter(this);
        presenter.initialize();
    }

    @Override
    public void onStart() {
        super.onStart();
        presenter.registerForStickyEvents();
    }

    @Override
    public void onStop() {
        presenter.unregisterForEvents();
        super.onStop();
    }

    @Override
    public void setTitle(String title) {
        setToolbarTitle(title);
    }
}
