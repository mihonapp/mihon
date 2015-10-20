package eu.kanade.mangafeed.util;

import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.ViewTarget;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.File;

import eu.kanade.mangafeed.R;

public class PageFileTarget extends ViewTarget<SubsamplingScaleImageView, File> {
    public static final String TAG = PageFileTarget.class.getSimpleName();

    public PageFileTarget(SubsamplingScaleImageView view) {
        super(view);
    }

    @Override
    public void onLoadCleared(Drawable placeholder) {
        view.setImage(ImageSource.resource(R.drawable.ic_action_refresh));
    }

    @Override
    public void onLoadStarted(Drawable placeholder) {
        view.setImage(ImageSource.resource(R.drawable.ic_action_refresh));
    }

    @Override
    public void onResourceReady(File resource, GlideAnimation<? super File> glideAnimation) {
        view.setImage(ImageSource.uri(Uri.fromFile(resource)));
    }
}
