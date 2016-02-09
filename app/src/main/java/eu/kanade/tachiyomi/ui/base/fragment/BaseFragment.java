package eu.kanade.tachiyomi.ui.base.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.greenrobot.eventbus.EventBus;

import eu.kanade.tachiyomi.ui.base.activity.BaseActivity;
import icepick.Icepick;

public class BaseFragment extends Fragment {

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Icepick.restoreInstanceState(this, savedState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    public void setToolbarTitle(String title) {
        getBaseActivity().setToolbarTitle(title);
    }

    public void setToolbarTitle(int resourceId) {
        getBaseActivity().setToolbarTitle(getString(resourceId));
    }

    public BaseActivity getBaseActivity() {
        return (BaseActivity) getActivity();
    }

    public void registerForEvents() {
        EventBus.getDefault().register(this);
    }

    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }

}
