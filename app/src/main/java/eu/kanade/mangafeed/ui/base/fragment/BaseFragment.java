package eu.kanade.mangafeed.ui.base.fragment;

import android.support.v4.app.Fragment;

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

}
