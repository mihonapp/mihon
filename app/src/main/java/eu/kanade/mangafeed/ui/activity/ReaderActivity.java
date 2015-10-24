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

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.presenter.ReaderPresenter;
import eu.kanade.mangafeed.ui.activity.base.BaseRxActivity;
import eu.kanade.mangafeed.ui.viewer.LeftToRightViewer;
import eu.kanade.mangafeed.ui.viewer.base.BaseViewer;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(ReaderPresenter.class)
public class ReaderActivity extends BaseRxActivity<ReaderPresenter> {

    @Bind(R.id.page_number) TextView pageNumber;
    @Bind(R.id.viewer) FrameLayout container;

    private int currentPage;
    private BaseViewer viewer;

    public static Intent newInstance(Context context) {
        return new Intent(context, ReaderActivity.class);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_reader);
        ButterKnife.bind(this);

        viewer = new LeftToRightViewer(this, container);

        enableHardwareAcceleration();
    }

    @Override
    public void onDestroy() {
        getPresenter().setCurrentPage(currentPage);
        super.onDestroy();
    }

    public void onPageListReady(List<Page> pages) {
        viewer.onPageListReady(pages);
    }

    public void onImageReady(Page page) {
        viewer.onImageReady(page);
    }

    public void onPageChanged(int currentPage, int totalPages) {
        String page = currentPage + "/" + totalPages;
        pageNumber.setText(page);
    }

    public void setCurrentPage(int page) {
        currentPage = page;
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
}
