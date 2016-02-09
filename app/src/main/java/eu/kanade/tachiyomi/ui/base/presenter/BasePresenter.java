package eu.kanade.tachiyomi.ui.base.presenter;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import icepick.Icepick;
import nucleus.view.ViewWithPresenter;

public class BasePresenter<V extends ViewWithPresenter> extends RxPresenter<V> {

    private Context context;

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

    public void registerForEvents() {
        EventBus.getDefault().register(this);
    }

    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }

    public void setContext(Context applicationContext) {
        context = applicationContext;
    }

    public Context getContext() {
        return context;
    }

}
