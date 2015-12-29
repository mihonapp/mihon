package eu.kanade.mangafeed.ui.base.fragment;

import android.support.v4.app.Fragment;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;

public class BaseFragment extends Fragment {

    public void setToolbarTitle(String title) {
        getBaseActivity().setToolbarTitle(title);
    }

    public void setToolbarTitle(int resourceId) {
        getBaseActivity().setToolbarTitle(getString(resourceId));
    }

    public BaseActivity getBaseActivity() {
        return (BaseActivity) getActivity();
    }

    public void registerForStickyEvents() {
        registerForStickyEvents(0);
    }

    public void registerForStickyEvents(int priority) {
        EventBus.getDefault().registerSticky(this, priority);
    }

    public void registerForEvents() {
        registerForEvents(0);
    }

    public void registerForEvents(int priority) {
        EventBus.getDefault().register(this, priority);
    }

    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }

}
