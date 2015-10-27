package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;

public class ReaderPageFragment extends Fragment {

    @Bind(R.id.page_image_view) SubsamplingScaleImageView imageView;
    @Bind(R.id.progress) ProgressBar progressBar;
    @Bind(R.id.image_error) TextView errorText;

    private Page page;

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
                progressBar.setVisibility(View.GONE);
                break;
            case (Page.DOWNLOAD):
                progressBar.setVisibility(View.VISIBLE);
                break;
            case (Page.ERROR):
                progressBar.setVisibility(View.GONE);
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

        loadImage();

        return view;
    }

}
