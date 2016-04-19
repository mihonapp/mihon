package nucleus.factory;

import nucleus.presenter.Presenter;

public interface PresenterFactory<P extends Presenter> {
    P createPresenter();
}
