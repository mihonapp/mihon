package eu.kanade.mangafeed.presenter;

import android.os.Bundle;
import android.support.annotation.NonNull;

import de.greenrobot.event.EventBus;
import icepick.Icepick;
import nucleus.presenter.RxPresenter;
import nucleus.view.ViewWithPresenter;

public class BasePresenter<V extends ViewWithPresenter> extends RxPresenter<V> {

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Icepick.restoreInstanceState(this, savedState);
    }

    @Override
    protected void onSave(@NonNull Bundle state) {
        super.onSave(state);
        Icepick.saveInstanceState(this, state);
    }

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
