package eu.kanade.mangafeed.ui.reader;

import android.app.Dialog;
import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import rx.Subscription;

public class ReaderMenu {

    @Bind(R.id.reader_menu) RelativeLayout menu;
    @Bind(R.id.reader_menu_bottom) LinearLayout bottomMenu;
    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.current_page) TextView currentPage;
    @Bind(R.id.page_seeker) SeekBar seekBar;
    @Bind(R.id.total_pages) TextView totalPages;
    @Bind(R.id.lock_orientation) ImageButton lockOrientation;
    @Bind(R.id.reader_selector) ImageButton readerSelector;
    @Bind(R.id.reader_extra_settings) ImageButton extraSettings;
    @Bind(R.id.reader_brightness) ImageButton brightnessSettings;

    private ReaderActivity activity;
    private PreferencesHelper preferences;

    @State boolean showing;
    private PopupWindow settingsPopup;
    private PopupWindow brightnessPopup;

    private DecimalFormat decimalFormat;

    public ReaderMenu(ReaderActivity activity) {
        this.activity = activity;
        this.preferences = activity.getPreferences();
        ButterKnife.bind(this, activity);

        // Intercept all image events in this layout
        bottomMenu.setOnTouchListener((v, event) -> true);

        seekBar.setOnSeekBarChangeListener(new PageSeekBarChangeListener());
        decimalFormat = new DecimalFormat("#.##");

        initializeOptions();
    }

    public void add(Subscription subscription) {
        activity.subscriptions.add(subscription);
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

        settingsPopup.dismiss();
        brightnessPopup.dismiss();

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
        add(preferences.lockOrientation().asObservable()
                .subscribe(locked -> {
                    int resourceId = !locked ? R.drawable.ic_screen_rotation :
                            activity.getResources().getConfiguration().orientation == 1 ?
                                    R.drawable.ic_screen_lock_portrait :
                                    R.drawable.ic_screen_lock_landscape;

                    lockOrientation.setImageResource(resourceId);
                }));

        lockOrientation.setOnClickListener(v ->
                preferences.lockOrientation().set(!preferences.lockOrientation().get()));

        // Reader selector
        readerSelector.setOnClickListener(v -> {
            final Manga manga = activity.getPresenter().getManga();
            final Dialog dialog = new AlertDialog.Builder(activity)
                    .setSingleChoiceItems(R.array.viewers_selector, manga.viewer, (d, which) -> {
                        if (manga.viewer != which) {
                            activity.setMangaDefaultViewer(which);
                        }
                        d.dismiss();
                    })
                    .create();
            showImmersiveDialog(dialog);
        });

        // Extra settings menu
        final View popupView = activity.getLayoutInflater().inflate(R.layout.reader_popup, null);
        settingsPopup = new SettingsPopupWindow(popupView);

        extraSettings.setOnClickListener(v -> {
            if (!settingsPopup.isShowing())
                settingsPopup.showAtLocation(extraSettings,
                        Gravity.BOTTOM | Gravity.RIGHT, 0, bottomMenu.getHeight());
            else
                settingsPopup.dismiss();
        });

        // Brightness popup
        final View brightnessView = activity.getLayoutInflater().inflate(R.layout.reader_brightness, null);
        brightnessPopup = new BrightnessPopupWindow(brightnessView);

        brightnessSettings.setOnClickListener(v -> {
            if (!brightnessPopup.isShowing())
                brightnessPopup.showAtLocation(brightnessSettings,
                        Gravity.BOTTOM | Gravity.LEFT, 0, bottomMenu.getHeight());
            else
                brightnessPopup.dismiss();
        });
    }

    private void showImmersiveDialog(Dialog dialog) {
        // Hack to not leave immersive mode
        dialog.getWindow().setFlags(LayoutParams.FLAG_NOT_FOCUSABLE,
                LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.getWindow().getDecorView().setSystemUiVisibility(
                activity.getWindow().getDecorView().getSystemUiVisibility());
        dialog.show();
        dialog.getWindow().clearFlags(LayoutParams.FLAG_NOT_FOCUSABLE);
        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        wm.updateViewLayout(activity.getWindow().getDecorView(), activity.getWindow().getAttributes());
    }

    class SettingsPopupWindow extends PopupWindow {

        @Bind(R.id.enable_transitions) CheckBox enableTransitions;
        @Bind(R.id.show_page_number) CheckBox showPageNumber;
        @Bind(R.id.hide_status_bar) CheckBox hideStatusBar;
        @Bind(R.id.keep_screen_on) CheckBox keepScreenOn;

        public SettingsPopupWindow(View view) {
            super(view, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            setAnimationStyle(R.style.reader_settings_popup_animation);
            ButterKnife.bind(this, view);
            initializePopupMenu();
        }

        private void initializePopupMenu() {
            // Load values from preferences
            enableTransitions.setChecked(preferences.enableTransitions().get());
            showPageNumber.setChecked(preferences.showPageNumber().get());
            hideStatusBar.setChecked(preferences.hideStatusBar().get());
            keepScreenOn.setChecked(preferences.keepScreenOn().get());

            // Add a listener to change the corresponding setting
            enableTransitions.setOnCheckedChangeListener((view, isChecked) ->
                    preferences.enableTransitions().set(isChecked));

            showPageNumber.setOnCheckedChangeListener((view, isChecked) ->
                    preferences.showPageNumber().set(isChecked));

            hideStatusBar.setOnCheckedChangeListener((view, isChecked) ->
                    preferences.hideStatusBar().set(isChecked));

            keepScreenOn.setOnCheckedChangeListener((view, isChecked) ->
                    preferences.keepScreenOn().set(isChecked));
        }

    }

    class BrightnessPopupWindow extends PopupWindow {

        @Bind(R.id.custom_brightness) CheckBox customBrightness;
        @Bind(R.id.brightness_seekbar) SeekBar brightnessSeekbar;

        public BrightnessPopupWindow(View view) {
            super(view, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            setAnimationStyle(R.style.reader_brightness_popup_animation);
            ButterKnife.bind(this, view);
            initializePopupMenu();
        }

        private void initializePopupMenu() {
            add(preferences.customBrightness()
                    .asObservable()
                    .subscribe(isEnabled -> {
                        customBrightness.setChecked(isEnabled);
                        brightnessSeekbar.setEnabled(isEnabled);
                    }));

            customBrightness.setOnCheckedChangeListener((view, isChecked) ->
                    preferences.customBrightness().set(isChecked));

            brightnessSeekbar.setMax(100);
            brightnessSeekbar.setProgress(Math.round(
                    preferences.customBrightnessValue().get() * brightnessSeekbar.getMax()));
            brightnessSeekbar.setOnSeekBarChangeListener(new BrightnessSeekBarChangeListener());
        }

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

    class BrightnessSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                preferences.customBrightnessValue().set((float) progress / seekBar.getMax());
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
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
