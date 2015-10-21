package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.ui.activity.BaseActivity;
import nucleus.factory.PresenterFactory;
import nucleus.presenter.Presenter;
import nucleus.view.NucleusSupportFragment;

public class BaseFragment<P extends Presenter> extends NucleusSupportFragment<P> {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        final PresenterFactory<P> superFactory = super.getPresenterFactory();
        setPresenterFactory(() -> {
            P presenter = superFactory.createPresenter();
            App.getComponentReflection(getActivity()).inject(presenter);
            return presenter;
        });
        super.onCreate(savedInstanceState);
    }

    public void setToolbarTitle(String title) {
        ((BaseActivity)getActivity()).setToolbarTitle(title);
    }

    public void setToolbarTitle(int resourceId) {
        ((BaseActivity)getActivity()).setToolbarTitle(getString(resourceId));
    }

}