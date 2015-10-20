package eu.kanade.mangafeed.ui.adapter;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import java.util.List;

import eu.kanade.mangafeed.ui.fragment.ReaderPageFragment;

public class ReaderPageAdapter extends SmartFragmentStatePagerAdapter {

    private List<String> imageUrls;

    public ReaderPageAdapter(FragmentManager fragmentManager) {
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
        return ReaderPageFragment.newInstance(imageUrls.get(position), position);
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

}
