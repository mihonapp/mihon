package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.util.MangaImageRegionDecoder;
import eu.kanade.mangafeed.util.PageFileTarget;

public class ReaderPageFragment extends Fragment {
    public static final String URL_ARGUMENT_KEY = "UrlArgumentKey";

    private SubsamplingScaleImageView imageView;

    private String mUrl;

    public static ReaderPageFragment newInstance(Page page) {
        ReaderPageFragment newInstance = new ReaderPageFragment();
        Bundle arguments = new Bundle();
        arguments.putString(URL_ARGUMENT_KEY, page.getImageUrl());
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
                mUrl = arguments.getString(URL_ARGUMENT_KEY);
            }
        }
    }

    public void setPage(Page page) {
        if (!page.getImageUrl().equals(mUrl)) {
            mUrl = page.getImageUrl();
            loadImage();
        }
    }

    private void loadImage() {
        if (mUrl != null) {
            Glide.with(getActivity())
                    .load(mUrl)
                    .downloadOnly(new PageFileTarget(imageView));
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        imageView = (SubsamplingScaleImageView)inflater.inflate(R.layout.fragment_page, container, false);
        imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
        imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
        imageView.setOnTouchListener((view, motionEvent) ->
                ((ReaderActivity) getActivity()).getViewPager().onImageTouch(motionEvent));

        loadImage();

        return imageView;
    }

}
