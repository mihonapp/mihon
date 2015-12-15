package eu.kanade.mangafeed.ui.reader.viewer.common;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.base.fragment.BaseFragment;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class ViewPagerReaderFragment extends BaseFragment {

    @Bind(R.id.page_image_view) SubsamplingScaleImageView imageView;
    @Bind(R.id.progress_container) LinearLayout progressContainer;
    @Bind(R.id.progress) ProgressBar progressBar;
    @Bind(R.id.progress_text) TextView progressText;
    @Bind(R.id.retry_button) Button retryButton;

    private ReaderActivity activity;
    private Page page;
    private Subscription progressSubscription;
    private Subscription statusSubscription;

    public static ViewPagerReaderFragment newInstance(Page page) {
        ViewPagerReaderFragment fragment = new ViewPagerReaderFragment();
        fragment.setPage(page);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_page, container, false);
        ButterKnife.bind(this, view);
        activity = (ReaderActivity) getActivity();

        if (activity.getReaderTheme() == ReaderActivity.BLACK_THEME) {
             progressText.setTextColor(ContextCompat.getColor(getContext(), R.color.light_grey));
        }

        imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
        imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
        imageView.setOnTouchListener((v, motionEvent) ->
                activity.getViewer().onImageTouch(motionEvent));

        retryButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (page != null)
                    activity.getPresenter().retryPage(page);
                return true;
            }
            return true;
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    public void onStart() {
        super.onStart();
        observeStatus();
    }

    @Override
    public void onStop() {
        unsubscribeProgress();
        unsubscribeStatus();
        super.onStop();
    }

    public void setPage(Page page) {
        this.page = page;
    }

    private void showImage() {
        if (page == null || page.getImagePath() == null)
            return;

        imageView.setImage(ImageSource.uri(page.getImagePath()).tilingDisabled());
        progressContainer.setVisibility(View.GONE);
    }

    private void showDownloading() {
        progressContainer.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
    }

    private void showLoading() {
        progressContainer.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progressText.setText(R.string.downloading);
    }

    private void showError() {
        progressContainer.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        retryButton.setVisibility(View.GONE);
    }

    private void processStatus(int status) {
        switch (status) {
            case Page.QUEUE:
                hideError();
                break;
            case Page.LOAD_PAGE:
                showLoading();
                break;
            case Page.DOWNLOAD_IMAGE:
                observeProgress();
                showDownloading();
                break;
            case Page.READY:
                showImage();
                unsubscribeProgress();
                unsubscribeStatus();
                break;
            case Page.ERROR:
                showError();
                unsubscribeProgress();
                break;
        }
    }

    private void observeStatus() {
        if (page == null || statusSubscription != null)
            return;

        PublishSubject<Integer> statusSubject = PublishSubject.create();
        page.setStatusSubject(statusSubject);

        statusSubscription = statusSubject
                .startWith(page.getStatus())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::processStatus);
    }

    private void observeProgress() {
        if (progressSubscription != null)
            return;

        final AtomicInteger currentValue = new AtomicInteger(-1);

        progressSubscription = Observable.interval(75, TimeUnit.MILLISECONDS, Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tick -> {
                    // Refresh UI only if progress change
                    if (page.getProgress() != currentValue.get()) {
                        currentValue.set(page.getProgress());
                        progressText.setText(getString(R.string.download_progress, page.getProgress()));
                    }
                });
    }

    private void unsubscribeStatus() {
        if (statusSubscription != null) {
            page.setStatusSubject(null);
            statusSubscription.unsubscribe();
            statusSubscription = null;
        }
    }

    private void unsubscribeProgress() {
        if (progressSubscription != null) {
            progressSubscription.unsubscribe();
            progressSubscription = null;
        }
    }

}
