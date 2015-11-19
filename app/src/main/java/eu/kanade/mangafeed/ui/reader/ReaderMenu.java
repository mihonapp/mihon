package eu.kanade.mangafeed.ui.reader;

import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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

public class ReaderMenu {

    @Bind(R.id.reader_menu) RelativeLayout menu;
    @Bind(R.id.reader_menu_bottom) LinearLayout bottomMenu;
    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.current_page) TextView currentPage;
    @Bind(R.id.page_seeker) SeekBar seekBar;
    @Bind(R.id.total_pages) TextView totalPages;

    private ReaderActivity activity;
    private PreferencesHelper preferences;
    private boolean showing;
    private DecimalFormat decimalFormat;

    public ReaderMenu(ReaderActivity activity, PreferencesHelper preferences) {
        this.activity = activity;
        this.preferences = preferences;
        ButterKnife.bind(this, activity);

        seekBar.setOnSeekBarChangeListener(new PageSeekBarChangeListener());
        decimalFormat = new DecimalFormat("#.##");
    }

    public void toggle() {
        if (showing)
            hide();
        else
            show();
    }

    private void show() {
        menu.setVisibility(View.VISIBLE);

        Animation toolbarAnimation = AnimationUtils.loadAnimation(activity, R.anim.enter_from_top);
        toolbar.startAnimation(toolbarAnimation);

        Animation bottomMenuAnimation = AnimationUtils.loadAnimation(activity, R.anim.enter_from_bottom);
        bottomMenu.startAnimation(bottomMenuAnimation);

        showing = true;
    }

    private void hide() {
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
