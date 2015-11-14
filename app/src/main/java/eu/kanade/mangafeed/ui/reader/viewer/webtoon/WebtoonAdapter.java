package eu.kanade.mangafeed.ui.reader.viewer.webtoon;

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.source.model.Page;
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

    public void addPage(Page page) {
        pages.add(page);
        notifyItemChanged(page.getPageNumber());
    }

    @LayoutId(R.layout.item_webtoon_reader)
    static class ImageViewHolder extends ItemViewHolder<Page> {

        @ViewId(R.id.page_image_view) SubsamplingScaleImageView imageView;
        @ViewId(R.id.progress) ProgressBar progressBar;

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
                progressBar.setVisibility(View.GONE);
            } else {
                imageView.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
            }

        }
    }
}
