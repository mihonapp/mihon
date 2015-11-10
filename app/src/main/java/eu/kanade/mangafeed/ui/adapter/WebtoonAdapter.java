package eu.kanade.mangafeed.ui.adapter;

import android.content.Context;
import android.view.View;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import uk.co.ribot.easyadapter.BaseEasyRecyclerAdapter;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;

public class WebtoonAdapter extends BaseEasyRecyclerAdapter<Page> {

    List<Page> pages;

    public WebtoonAdapter(Context context) {
        super(context, ImageViewHolder.class);
        pages = new ArrayList<>();
    }

    @Override
    public Page getItem(int position) {
        return pages.get(position);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
        notifyDataSetChanged();
    }

    @LayoutId(R.layout.chapter_image)
    static class ImageViewHolder extends ItemViewHolder<Page> {

        @ViewId(R.id.page_image_view) SubsamplingScaleImageView imageView;

        public ImageViewHolder(View view) {
            super(view);
            imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
            imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
            imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
            imageView.setZoomEnabled(false);
            imageView.setPanEnabled(false);
        }

        @Override
        public void onSetValues(Page page, PositionInfo positionInfo) {
            if (page.getImagePath() != null) {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImage(ImageSource.uri(page.getImagePath()).tilingDisabled());
            } else {
                imageView.setVisibility(View.GONE);
            }

        }
    }
}
