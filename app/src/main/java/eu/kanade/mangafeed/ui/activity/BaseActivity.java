package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import eu.kanade.mangafeed.App;
import nucleus.factory.PresenterFactory;
import nucleus.presenter.Presenter;
import nucleus.view.NucleusAppCompatActivity;

public class BaseActivity<P extends Presenter> extends NucleusAppCompatActivity<P> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final PresenterFactory<P> superFactory = super.getPresenterFactory();
        setPresenterFactory(() -> {
            P presenter = superFactory.createPresenter();
            App.getComponentReflection(getActivity()).inject(presenter);
            return presenter;
        });
        super.onCreate(savedInstanceState);
    }

    protected void setupToolbar(Toolbar toolbar) {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public void setToolbarTitle(String title) {
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(title);
    }

    public void setToolbarTitle(int titleResource) {
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(getString(titleResource));
    }

    public Context getActivity() {
        return this;
    }
}