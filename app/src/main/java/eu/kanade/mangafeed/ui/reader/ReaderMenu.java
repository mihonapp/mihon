package eu.kanade.mangafeed.ui.reader;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.DecimalFormat;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import icepick.State;
import rx.subscriptions.CompositeSubscription;

public class ReaderMenu {

    @Bind(R.id.reader_menu) RelativeLayout menu;
    @Bind(R.id.reader_menu_bottom) LinearLayout bottomMenu;
    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.current_page) TextView currentPage;
    @Bind(R.id.page_seeker) SeekBar seekBar;
    @Bind(R.id.total_pages) TextView totalPages;
    @Bind(R.id.lock_orientation) ImageButton lockOrientation;
    @Bind(R.id.reader_selector) ImageButton readerSelector;


    private ReaderActivity activity;
    private PreferencesHelper preferences;
    @State boolean showing;
    private DecimalFormat decimalFormat;

    private CompositeSubscription subscriptions;

    public ReaderMenu(ReaderActivity activity, PreferencesHelper preferences) {
        this.activity = activity;
        this.preferences = preferences;
        ButterKnife.bind(this, activity);

        // Intercept all image events in this layout
        bottomMenu.setOnTouchListener((v, event) -> true);

        seekBar.setOnSeekBarChangeListener(new PageSeekBarChangeListener());
        decimalFormat = new DecimalFormat("#.##");

        subscriptions = new CompositeSubscription();
        initializeOptions();
    }

    public void destroy() {
        subscriptions.unsubscribe();
    }

    public void toggle() {
        if (showing)
            hide();
        else
            show(true);
    }

    public void show(boolean animate) {
        menu.setVisibility(View.VISIBLE);

        if (animate) {
            Animation toolbarAnimation = AnimationUtils.loadAnimation(activity, R.anim.enter_from_top);
            toolbar.startAnimation(toolbarAnimation);

            Animation bottomMenuAnimation = AnimationUtils.loadAnimation(activity, R.anim.enter_from_bottom);
            bottomMenu.startAnimation(bottomMenuAnimation);
        }

        showing = true;
    }

    public void hide() {
        Animation toolbarAnimation = AnimationUtils.loadAnimation(activity, R.anim.exit_to_top);
        toolbarAnimation.setAnimationListener(new HideMenuAnimationListener());
        toolbar.startAnimation(toolbarAnimation);

        Animation bottomMenuAnimation = AnimationUtils.loadAnimation(activity, R.anim.exit_to_bottom);
        bottomMenu.startAnimation(bottomMenuAnimation);

        showing = false;
    }

    public void onChapterReady(int numPages, Manga manga, Chapter chapter) {
        totalPages.setText("" + numPages);
        seekBar.setMax(numPages - 1);

        activity.setToolbarTitle(manga.title);
        activity.setToolbarSubtitle(chapter.chapter_number != -1 ?
                activity.getString(R.string.chapter_subtitle,
                        decimalFormat.format(chapter.chapter_number)) :
                chapter.name);

    }

    public void onPageChanged(int pageIndex) {
        currentPage.setText("" + (pageIndex + 1));
        seekBar.setProgress(pageIndex);
    }

    private void initializeOptions() {
        // Orientation changes
        lockOrientation.setOnClickListener(v ->
                preferences.setOrientationLocked(!preferences.isOrientationLocked()));

        subscriptions.add(preferences.isOrientationLockedObservable()
                .subscribe(this::onOrientationOptionChanged));

        // Reader selector
        readerSelector.setOnClickListener(v -> {
            final Manga manga = activity.getPresenter().getManga();
            final Dialog dialog = new AlertDialog.Builder(activity)
                    .setSingleChoiceItems(R.array.viewers_selector, manga.viewer, (d, which) -> {
                        if (manga.viewer != which) {
                            activity.getPresenter().updateMangaViewer(which);
                            activity.recreate();
                        }
                        d.dismiss();
                    })
                    .create();

            // Hack to not leave immersive mode
            dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            dialog.getWindow().getDecorView().setSystemUiVisibility(
                    activity.getWindow().getDecorView().getSystemUiVisibility());
            dialog.show();
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(activity.getWindow().getDecorView(), activity.getWindow().getAttributes());
        });
    }

    private void onOrientationOptionChanged(boolean locked) {
        if (locked)
            lockOrientation();
        else
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        int resourceId = !locked ? R.drawable.ic_screen_rotation :
                activity.getResources().getConfiguration().orientation == 1 ?
                        R.drawable.ic_screen_lock_portrait :
                        R.drawable.ic_screen_lock_landscape;

        lockOrientation.setImageResource(resourceId);
    }

    private void lockOrientation() {
        int orientation;
        int rotation = ((WindowManager) activity.getSystemService(
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
        activity.setRequestedOrientation(orientation);
    }

    class PageSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                activity.setSelectedPage(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    class HideMenuAnimationListener implements Animation.AnimationListener {

        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
            menu.setVisibility(View.GONE);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
    }
}
