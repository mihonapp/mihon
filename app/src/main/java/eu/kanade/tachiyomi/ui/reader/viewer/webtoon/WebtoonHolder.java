package eu.kanade.tachiyomi.ui.reader.viewer.webtoon;

import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.source.model.Page;

public class WebtoonHolder extends RecyclerView.ViewHolder {

    @Bind(R.id.page_image_view) SubsamplingScaleImageView imageView;
    @Bind(R.id.frame_container) ViewGroup container;
    @Bind(R.id.progress) ProgressBar progressBar;
    @Bind(R.id.retry_button) Button retryButton;

    private Animation fadeInAnimation;
    private Page page;
    private WebtoonAdapter adapter;

    public WebtoonHolder(View view, WebtoonAdapter adapter, View.OnTouchListener touchListener) {
        super(view);
        this.adapter = adapter;
        ButterKnife.bind(this, view);

        fadeInAnimation = AnimationUtils.loadAnimation(view.getContext(), R.anim.fade_in);

        imageView.setParallelLoadingEnabled(true);
        imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
        imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
        imageView.setOnTouchListener(touchListener);
        imageView.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
            @Override
            public void onImageLoaded() {
                imageView.startAnimation(fadeInAnimation);
            }
        });
        progressBar.setMinimumHeight(view.getResources().getDisplayMetrics().heightPixels);

        container.setOnTouchListener(touchListener);
        retryButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (page != null)
                    adapter.retryPage(page);
                return true;
            }
            return true;
        });
    }

    public void onSetValues(Page page) {
        this.page = page;
        switch (page.getStatus()) {
            case Page.QUEUE:
                onQueue();
                break;
            case Page.LOAD_PAGE:
                onLoading();
                break;
            case Page.DOWNLOAD_IMAGE:
                onLoading();
                break;
            case Page.READY:
                onReady();
                break;
            case Page.ERROR:
                onError();
                break;
        }
    }

    private void onLoading() {
        setErrorButtonVisible(false);
        setImageVisible(false);
        setProgressVisible(true);
    }

    private void onReady() {
        setErrorButtonVisible(false);
        setProgressVisible(false);
        setImageVisible(true);
        imageView.setRegionDecoderClass(adapter.getReader().getRegionDecoderClass());
        imageView.setImage(ImageSource.uri(page.getImagePath()));
    }

    private void onError() {
        setImageVisible(false);
        setProgressVisible(false);
        setErrorButtonVisible(true);
    }

    private void onQueue() {
        setImageVisible(false);
        setErrorButtonVisible(false);
        setProgressVisible(false);
    }

    private void setProgressVisible(boolean visible) {
        progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setImageVisible(boolean visible) {
        imageView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setErrorButtonVisible(boolean visible) {
        retryButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}