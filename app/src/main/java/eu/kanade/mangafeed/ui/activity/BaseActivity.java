package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import eu.kanade.mangafeed.App;
import nucleus.factory.PresenterFactory;
import nucleus.presenter.Presenter;
import nucleus.view.NucleusAppCompatActivity;
import timber.log.Timber;

public class BaseActivity<P extends Presenter> extends NucleusAppCompatActivity<P> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final PresenterFactory<P> superFactory = super.getPresenterFactory();
        setPresenterFactory(() -> {
            P presenter = superFactory.createPresenter();
            try {
                App.getComponentReflection(getActivity()).inject(presenter);
            } catch(Exception e) {
                Timber.w("No injection for " + presenter.getClass().toString());
            }
            return presenter;
        });
        super.onCreate(savedInstanceState);
    }

    protected void setupToolbar(Toolbar toolbar) {
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public void setToolbarTitle(String title) {
        getSupportActionBar().setTitle(title);
    }

    public Context getActivity() {
        return this;
    }
}