package eu.kanade.mangafeed.ui.viewer;

import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.ui.adapter.ViewerPagerAdapter;
import eu.kanade.mangafeed.ui.viewer.base.BaseViewer;
import eu.kanade.mangafeed.widget.HorizontalViewPager;
import fr.castorflex.android.verticalviewpager.VerticalViewPager;

public class VerticalViewer extends BaseViewer {

    @Bind(R.id.view_pager) VerticalViewPager viewPager;
    private ViewerPagerAdapter adapter;

    public VerticalViewer(ReaderActivity activity, FrameLayout container) {
        super(activity, container);
        activity.getLayoutInflater().inflate(R.layout.viewer_verticalviewpager, container);
        ButterKnife.bind(this, container);

        adapter = new ViewerPagerAdapter(activity.getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);
        viewPager.setOnPageChangeListener(new HorizontalViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updatePageNumber();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    @Override
    public int getTotalPages() {
        return adapter.getCount();
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        viewPager.setCurrentItem(getCurrentPageIndex(pageNumber));
    }

    @Override
    public void onPageListReady(List<Page> pages) {
        adapter.setPages(pages);
    }

    @Override
    public boolean onImageTouch(MotionEvent motionEvent) {
        return true;
    }
}
