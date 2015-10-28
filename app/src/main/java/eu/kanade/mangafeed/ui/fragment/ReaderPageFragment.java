package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class ReaderPageFragment extends Fragment {

    @Bind(R.id.page_image_view) SubsamplingScaleImageView imageView;
    @Bind(R.id.progress_container) LinearLayout progressContainer;
    @Bind(R.id.progress) ProgressBar progressBar;
    @Bind(R.id.progress_text) TextView progressText;
    @Bind(R.id.image_error) TextView errorText;

    private Page page;
    private Subscription progressSubscription;

    public static ReaderPageFragment newInstance(Page page) {
        ReaderPageFragment fragment = new ReaderPageFragment();
        fragment.setPage(page);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void replacePage(Page page) {
        unsubscribeProgress();
        this.page = page;
        loadImage();
    }

    public void setPage(Page page) {
        this.page = page;
    }

    private void loadImage() {
        if (page == null)
            return;

        switch (page.getStatus()) {
            case (Page.READY):
                imageView.setImage(ImageSource.uri(page.getImagePath()).tilingDisabled());
                progressContainer.setVisibility(View.GONE);
                break;
            case (Page.DOWNLOAD):
                progressContainer.setVisibility(View.VISIBLE);
                break;
            case (Page.ERROR):
                progressContainer.setVisibility(View.GONE);
                errorText.setVisibility(View.VISIBLE);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_page, container, false);
        ButterKnife.bind(this, view);

        imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
        imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
        imageView.setOnTouchListener((v, motionEvent) ->
                ((ReaderActivity) getActivity()).onImageTouch(motionEvent));

        observeProgress();
        loadImage();

        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        unsubscribeProgress();
    }

    private void observeProgress() {
        if (page == null || page.getStatus() != Page.DOWNLOAD)
            return;

        progressSubscription = Observable.interval(75, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tick -> {
                    if (page.getProgress() == 0) {
                        progressText.setText(R.string.downloading);
                    }
                    else if (page.getProgress() == 100) {
                        progressContainer.setVisibility(View.GONE);
                        unsubscribeProgress();
                    }
                    else {
                        progressText.setText(getString(R.string.download_progress, page.getProgress()));
                    }
                });
    }

    private void unsubscribeProgress() {
        if (progressSubscription != null)
            progressSubscription.unsubscribe();
    }

}
