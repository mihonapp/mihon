package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.content.Intent;
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

        viewer = getViewer();

        enableHardwareAcceleration();
    }
    
    public void onPageListReady(List<Page> pages) {
        viewer.onPageListReady(pages);
    }

    public void onPageChanged(int currentPageIndex, int totalPages) {
        if (currentPageIndex != 0)
            getPresenter().setCurrentPage(currentPageIndex);
        String page = (currentPageIndex + 1) + "/" + totalPages;
        pageNumber.setText(page);
    }

    public void setSelectedPage(int pageIndex) {
        viewer.setSelectedPage(pageIndex);
    }

    public void hideStatusBar() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    public void enableHardwareAcceleration() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    }

    public boolean onImageTouch(MotionEvent motionEvent) {
        return viewer.onImageTouch(motionEvent);
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
