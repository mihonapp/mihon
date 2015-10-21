package eu.kanade.mangafeed.ui.fragment.base;

import android.support.v4.app.Fragment;

import eu.kanade.mangafeed.ui.activity.base.BaseActivity;

public class BaseFragment extends Fragment {

    public void setToolbarTitle(String title) {
        ((BaseActivity)getActivity()).setToolbarTitle(title);
    }

    public void setToolbarTitle(int resourceId) {
        ((BaseActivity)getActivity()).setToolbarTitle(getString(resourceId));
    }

}
