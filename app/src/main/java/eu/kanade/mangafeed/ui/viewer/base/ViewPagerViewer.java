package eu.kanade.mangafeed.ui.viewer.base;

import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.ui.adapter.ViewerPagerAdapter;
import eu.kanade.mangafeed.widget.HorizontalViewPager;

public abstract class ViewPagerViewer extends BaseViewer {

    @Bind(R.id.view_pager)
    HorizontalViewPager viewPager;
    protected ViewerPagerAdapter adapter;

    public ViewPagerViewer(ReaderActivity activity, FrameLayout container) {
        super(activity, container);
        activity.getLayoutInflater().inflate(R.layout.viewer_viewpager, container);
        ButterKnife.bind(this, container);

        adapter = new ViewerPagerAdapter(activity.getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);
        viewPager.addOnPageChangeListener(new HorizontalViewPager.OnPageChangeListener() {
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
        viewPager.setOnChapterBoundariesOutListener(new HorizontalViewPager.OnChapterBoundariesOutListener() {
            @Override
            public void onFirstPageOutEvent() {
                onFirstPageOut();
            }

            @Override
            public void onLastPageOutEvent() {
                onLastPageOut();
            }
        });
        viewPager.setOnChapterSingleTapListener(activity::onCenterSingleTap);
    }

    public HorizontalViewPager getViewPager() {
        return viewPager;
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
        return viewPager.onImageTouch(motionEvent);
    }

    public abstract void onFirstPageOut();
    public abstract void onLastPageOut();

}
