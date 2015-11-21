package eu.kanade.mangafeed.ui.reader;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.base.activity.BaseRxActivity;
import eu.kanade.mangafeed.ui.reader.viewer.base.BaseReader;
import eu.kanade.mangafeed.ui.reader.viewer.horizontal.LeftToRightReader;
import eu.kanade.mangafeed.ui.reader.viewer.horizontal.RightToLeftReader;
import eu.kanade.mangafeed.ui.reader.viewer.vertical.VerticalReader;
import eu.kanade.mangafeed.ui.reader.viewer.webtoon.WebtoonReader;
import eu.kanade.mangafeed.util.ToastUtil;
import icepick.Icepick;
import nucleus.factory.RequiresPresenter;
import rx.subscriptions.CompositeSubscription;

@RequiresPresenter(ReaderPresenter.class)
public class ReaderActivity extends BaseRxActivity<ReaderPresenter> {

    @Bind(R.id.page_number) TextView pageNumber;
    @Bind(R.id.reader) FrameLayout container;
    @Bind(R.id.toolbar) Toolbar toolbar;

    @Inject PreferencesHelper preferences;

    private BaseReader viewer;
    private ReaderMenu readerMenu;

    private int uiFlags;
    private CompositeSubscription subscriptions;

    private static final int LEFT_TO_RIGHT = 1;
    private static final int RIGHT_TO_LEFT = 2;
    private static final int VERTICAL = 3;
    private static final int WEBTOON = 4;

    public static Intent newIntent(Context context) {
        return new Intent(context, ReaderActivity.class);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        App.get(this).getComponent().inject(this);
        setContentView(R.layout.activity_reader);
        ButterKnife.bind(this);

        setupToolbar(toolbar);
        subscriptions = new CompositeSubscription();

        readerMenu = new ReaderMenu(this);
        Icepick.restoreInstanceState(readerMenu, savedState);
        if (savedState != null && readerMenu.showing)
            readerMenu.show(false);

        enableHardwareAcceleration();

        initializeSettings();
    }

    @Override
    protected void onDestroy() {
        readerMenu.destroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setSystemUiVisibility();
    }

    @Override
    protected void onPause() {
        getPresenter().setCurrentPage(viewer.getCurrentPosition());
        viewer.destroySubscriptions();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Icepick.saveInstanceState(readerMenu, outState);
        super.onSaveInstanceState(outState);
    }

    private void createUiHideFlags(boolean statusBarHidden) {
        uiFlags = 0;
        uiFlags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (statusBarHidden)
            uiFlags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            uiFlags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }

    public void onChapterReady(List<Page> pages, Manga manga, Chapter chapter) {
        viewer = createViewer(manga);
        viewer.onPageListReady(pages);
        viewer.updatePageNumber();
        readerMenu.onChapterReady(pages.size(), manga, chapter);
    }

    public void onChapterError() {
        finish();
        ToastUtil.showShort(this, R.string.page_list_error);
    }

    public void onPageChanged(int currentPageIndex, int totalPages) {
        String page = (currentPageIndex + 1) + "/" + totalPages;
        pageNumber.setText(page);
        readerMenu.onPageChanged(currentPageIndex);
    }

    public void setSelectedPage(int pageIndex) {
        viewer.setSelectedPage(pageIndex);
    }

    public void setSystemUiVisibility() {
        getWindow().getDecorView().setSystemUiVisibility(uiFlags);
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
        readerMenu.toggle();
    }

    public ViewGroup getContainer() {
        return container;
    }

    public PreferencesHelper getPreferences() {
        return preferences;
    }

    private BaseReader createViewer(Manga manga) {
        int mangaViewer = manga.viewer == 0 ? preferences.getDefaultViewer() : manga.viewer;

        switch (mangaViewer) {
            case LEFT_TO_RIGHT: default:
                return new LeftToRightReader(this);
            case RIGHT_TO_LEFT:
                return new RightToLeftReader(this);
            case VERTICAL:
                return new VerticalReader(this);
            case WEBTOON:
                return new WebtoonReader(this);
        }
    }

    private void initializeSettings() {
        subscriptions.add(preferences.showPageNumber()
                .asObservable()
                .subscribe(this::setPageNumberVisibility));

        subscriptions.add(preferences.lockOrientation()
                .asObservable()
                .subscribe(this::setOrientation));

        subscriptions.add(preferences.hideStatusBar()
                .asObservable()
                .subscribe(this::setStatusBarVisibility));

        subscriptions.add(preferences.keepScreenOn()
                .asObservable()
                .subscribe(this::setKeepScreenOn));
    }

    private void setOrientation(boolean locked) {
        if (locked) {
            int orientation;
            int rotation = ((WindowManager) getSystemService(
                    Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
            }
            setRequestedOrientation(orientation);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void setPageNumberVisibility(boolean visible) {
        pageNumber.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    private void setStatusBarVisibility(boolean hidden) {
        createUiHideFlags(hidden);
        setSystemUiVisibility();
    }

    private void setKeepScreenOn(boolean enabled) {
        if (enabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
    
    protected void setMangaDefaultViewer(int viewer) {
        getPresenter().updateMangaViewer(viewer);
        recreate();
    }

}
