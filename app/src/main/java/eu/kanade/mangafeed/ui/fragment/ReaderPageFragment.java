package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;

public class ReaderPageFragment extends Fragment {
    public static final String URL_ARGUMENT_KEY = "UrlArgumentKey";

    @Bind(R.id.page_image_view) SubsamplingScaleImageView imageView;
    @Bind(R.id.progress) ProgressBar progressBar;

    private String imagePath;

    public static ReaderPageFragment newInstance(Page page) {
        ReaderPageFragment newInstance = new ReaderPageFragment();
        Bundle arguments = new Bundle();
        arguments.putString(URL_ARGUMENT_KEY, page.getImagePath());
        newInstance.setArguments(arguments);
        return newInstance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        Bundle arguments = getArguments();
        if (arguments != null) {
            if (arguments.containsKey(URL_ARGUMENT_KEY)) {
                imagePath = arguments.getString(URL_ARGUMENT_KEY);
            }
        }
    }

    public void setPage(Page page) {
        if (!page.getImageUrl().equals(imagePath)) {
            imagePath = page.getImagePath();
            loadImage();
        }
    }

    private void loadImage() {
        if (imagePath != null) {
            progressBar.setVisibility(View.GONE);
            imageView.setImage(ImageSource.uri(imagePath).tilingDisabled());
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

        progressBar.setVisibility(View.VISIBLE);

        loadImage();

        return view;
    }

}
