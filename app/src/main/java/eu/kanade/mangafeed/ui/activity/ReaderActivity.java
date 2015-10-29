package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.presenter.ReaderPresenter;
import eu.kanade.mangafeed.ui.activity.base.BaseRxActivity;
import eu.kanade.mangafeed.ui.viewer.LeftToRightViewer;
import eu.kanade.mangafeed.ui.viewer.RightToLeftViewer;
import eu.kanade.mangafeed.ui.viewer.VerticalViewer;
import eu.kanade.mangafeed.ui.viewer.WebtoonViewer;
import eu.kanade.mangafeed.ui.viewer.base.BaseViewer;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(ReaderPresenter.class)
public class ReaderActivity extends BaseRxActivity<ReaderPresenter> {

    @Bind(R.id.page_number) TextView pageNumber;
    @Bind(R.id.viewer) FrameLayout container;

    @Inject PreferencesHelper prefs;

    private BaseViewer viewer;
    private boolean isFullscreen;

    private static final int LEFT_TO_RIGHT = 1;
    private static final int RIGHT_TO_LEFT = 2;
    private static final int VERTICAL = 3;
    private static final int WEBTOON = 4;


    public static Intent newInstance(Context context) {
        return new Intent(context, ReaderActivity.class);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        App.get(this).getComponent().inject(this);
        setContentView(R.layout.activity_reader);
        ButterKnife.bind(this);

        if (prefs.useFullscreenSet())
            enableFullScreen();

        viewer = getViewer();

        enableHardwareAcceleration();
    }
    
    public void onPageListReady(List<Page> pages) {
        viewer.onPageListReady(pages);
        viewer.updatePageNumber();
    }

    public void onPageChanged(int currentPageIndex, int totalPages) {
        String page = (currentPageIndex + 1) + "/" + totalPages;
        pageNumber.setText(page);
    }

    @Override
    protected void onPause() {
        getPresenter().setCurrentPage(viewer.getCurrentPosition());
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void setSelectedPage(int pageIndex) {
        viewer.setSelectedPage(pageIndex);
    }

    public void enableFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
            );
        }
        isFullscreen = true;
    }

    public void disableFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.VISIBLE
            );
        }
        isFullscreen = false;
    }

    public void enableHardwareAcceleration() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    }

    public boolean onImageSingleTap(MotionEvent motionEvent) {
        return viewer.onImageTouch(motionEvent);
    }

    public void onCenterSingleTap() {
        toggleFullscreen();
    }

    private void toggleFullscreen() {
        if (isFullscreen)
            disableFullscreen();
        else
            enableFullScreen();
    }

    private BaseViewer getViewer() {
        switch (prefs.getDefaultViewer()) {
            case LEFT_TO_RIGHT: default:
                return new LeftToRightViewer(this, container);
            case RIGHT_TO_LEFT:
                return new RightToLeftViewer(this, container);
            case VERTICAL:
                return new VerticalViewer(this, container);
            case WEBTOON:
                return new WebtoonViewer(this, container);
        }
    }

}
