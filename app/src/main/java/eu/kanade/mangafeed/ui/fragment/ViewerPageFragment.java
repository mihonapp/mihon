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
import eu.kanade.mangafeed.util.PageFileTarget;

public class ViewerPageFragment extends Fragment {
    public static final String URL_ARGUMENT_KEY = "UrlArgumentKey";

    private SubsamplingScaleImageView mPageImageView;

    private String mUrl;

    public static ViewerPageFragment newInstance(String url, int position) {
        ViewerPageFragment newInstance = new ViewerPageFragment();
        Bundle arguments = new Bundle();
        arguments.putString(URL_ARGUMENT_KEY, url);
        newInstance.setArguments(arguments);
        return newInstance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        if (arguments != null) {
            if (arguments.containsKey(URL_ARGUMENT_KEY)) {
                mUrl = arguments.getString(URL_ARGUMENT_KEY);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mPageImageView = (SubsamplingScaleImageView)inflater.inflate(R.layout.fragment_page, container, false);
        mPageImageView.setVisibility(View.INVISIBLE);
        mPageImageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
        mPageImageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        mPageImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
        mPageImageView.setOnImageEventListener(new SubsamplingScaleImageView.OnImageEventListener() {
            @Override
            public void onReady() {
                mPageImageView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onImageLoaded() {
            }

            @Override
            public void onPreviewLoadError(Exception e) {
            }

            @Override
            public void onImageLoadError(Exception e) {
            }

            @Override
            public void onTileLoadError(Exception e) {
            }
        });

        return mPageImageView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Glide.with(getActivity())
                .load(mUrl)
                .downloadOnly(new PageFileTarget(mPageImageView));
    }
}
