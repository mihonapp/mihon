package eu.kanade.mangafeed.presenter;

import de.greenrobot.event.EventBus;

public class BasePresenter {

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
