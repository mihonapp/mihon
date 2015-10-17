package eu.kanade.mangafeed.presenter;

import de.greenrobot.event.EventBus;
import nucleus.presenter.RxPresenter;
import nucleus.view.ViewWithPresenter;

public class BasePresenter2<V extends ViewWithPresenter> extends RxPresenter<V> {

    public void registerForStickyEvents() {
        EventBus.getDefault().registerSticky(this);
    }

    public void registerForEvents() {
        EventBus.getDefault().register(this);
    }

    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }
}
