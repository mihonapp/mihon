package eu.kanade.tachiyomi.ui.reader.viewer.pager;

import android.graphics.PointF;
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

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.base.fragment.BaseFragment;
import eu.kanade.tachiyomi.ui.reader.ReaderActivity;
import eu.kanade.tachiyomi.ui.reader.viewer.base.PageDecodeErrorLayout;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.RightToLeftReader;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.vertical.VerticalReader;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class PagerReaderFragment extends BaseFragment {

    @Bind(R.id.page_image_view) SubsamplingScaleImageView imageView;
    @Bind(R.id.progress_container) LinearLayout progressContainer;
    @Bind(R.id.progress) ProgressBar progressBar;
    @Bind(R.id.progress_text) TextView progressText;
    @Bind(R.id.retry_button) Button retryButton;

    private Page page;
    private Subscription progressSubscription;
    private Subscription statusSubscription;
    private int position = -1;

    private int lightGreyColor;
    private int blackColor;

    public static PagerReaderFragment newInstance() {
        return new PagerReaderFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.item_pager_reader, container, false);
        ButterKnife.bind(this, view);
        ReaderActivity activity = getReaderActivity();
        PagerReader parentFragment = (PagerReader) getParentFragment();

        lightGreyColor = ContextCompat.getColor(getContext(), R.color.light_grey);
        blackColor = ContextCompat.getColor(getContext(), R.color.primary_text);

        if (activity.getReaderTheme() == ReaderActivity.BLACK_THEME) {
             progressText.setTextColor(lightGreyColor);
        }

        if (parentFragment instanceof RightToLeftReader) {
            view.setRotation(-180);
        }

        imageView.setParallelLoadingEnabled(true);
        imageView.setMaxBitmapDimensions(activity.getMaxBitmapSize());
        imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
        imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        imageView.setMinimumScaleType(parentFragment.scaleType);
        imageView.setMinimumDpi(50);
        imageView.setRegionDecoderClass(parentFragment.getRegionDecoderClass());
        imageView.setBitmapDecoderClass(parentFragment.getBitmapDecoderClass());
        imageView.setVerticalScrollingParent(parentFragment instanceof VerticalReader);
        imageView.setOnTouchListener((v, motionEvent) -> parentFragment.gestureDetector.onTouchEvent(motionEvent));
        imageView.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
            @Override
            public void onReady() {
                switch (parentFragment.zoomStart) {
                    case PagerReader.ALIGN_LEFT:
                        imageView.setScaleAndCenter(imageView.getScale(), new PointF(0, 0));
                        break;
                    case PagerReader.ALIGN_RIGHT:
                        imageView.setScaleAndCenter(imageView.getScale(), new PointF(imageView.getSWidth(), 0));
                        break;
                    case PagerReader.ALIGN_CENTER:
                        PointF center = imageView.getCenter();
                        center.y = 0;
                        imageView.setScaleAndCenter(imageView.getScale(), center);
                        break;
                }
            }

            @Override
            public void onImageLoadError(Exception e) {
                showImageDecodeError();
            }
        });

        retryButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                activity.getPresenter().retryPage(page);
            }
            return true;
        });

        observeStatus();
        return view;
    }

    @Override
    public void onDestroyView() {
        unsubscribeProgress();
        unsubscribeStatus();
        imageView.setOnTouchListener(null);
        imageView.setOnImageEventListener(null);
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    public void setPage(Page page) {
        this.page = page;

        // This method can be called before the view is created
        if (imageView != null) {
            observeStatus();
        }
    }

    public void setPosition(int position) {
        this.position = position;
    }

    private void showImage() {
        if (page == null || page.getImagePath() == null)
            return;

        File imagePath = new File(page.getImagePath());
        if (imagePath.exists()) {
            imageView.setImage(ImageSource.uri(page.getImagePath()));
            progressContainer.setVisibility(View.GONE);
        } else {
            page.setStatus(Page.ERROR);
        }
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

    private void showImageDecodeError() {
        ViewGroup view = (ViewGroup) getView();
        if (view == null)
            return;

        LinearLayout errorLayout = new PageDecodeErrorLayout(getContext(), page,
                getReaderActivity().getReaderTheme(),
                () -> getReaderActivity().getPresenter().retryPage(page));

        view.addView(errorLayout);
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

        progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS, Schedulers.newThread())
                .onBackpressureLatest()
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

    public Page getPage() {
        return page;
    }

    public int getPosition() {
        return position;
    }

    private ReaderActivity getReaderActivity() {
        return (ReaderActivity) getActivity();
    }

}
