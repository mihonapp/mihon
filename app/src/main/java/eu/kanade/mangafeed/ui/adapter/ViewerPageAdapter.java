package eu.kanade.mangafeed.ui.adapter;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import java.util.List;

import eu.kanade.mangafeed.ui.fragment.ViewerPageFragment;

public class ViewerPageAdapter extends SmartFragmentStatePagerAdapter {

    private List<String> imageUrls;

    public ViewerPageAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public int getCount() {
        if (imageUrls != null)
            return imageUrls.size();

        return 0;
    }

    @Override
    public Fragment getItem(int position) {
        return ViewerPageFragment.newInstance(imageUrls.get(position), position);
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

}
