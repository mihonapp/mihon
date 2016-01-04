package eu.kanade.mangafeed.ui.reader.viewer.webtoon;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.source.model.Page;

public class WebtoonAdapter extends RecyclerView.Adapter<WebtoonAdapter.ImageHolder> {

    private List<Page> pages;
    private WebtoonReader fragment;
    private int maxBitmapSize;
    private View.OnTouchListener listener;

    public WebtoonAdapter(WebtoonReader fragment) {
        this.fragment = fragment;
        pages = new ArrayList<>();
        maxBitmapSize = fragment.getReaderActivity().getMaxBitmapSize();
        listener = (v, event) -> fragment.onImageTouch(event);
    }

    public Page getItem(int position) {
        return pages.get(position);
    }

    @Override
    public ImageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = fragment.getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.item_webtoon_reader, parent, false);
        return new ImageHolder(v, maxBitmapSize, listener);
    }

    @Override
    public void onBindViewHolder(ImageHolder holder, int position) {
        final Page page = getItem(position);
        holder.onSetValues(page);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    public void addPage(Page page) {
        pages.add(page);
        notifyItemInserted(page.getPageNumber());
    }

    public static class ImageHolder extends RecyclerView.ViewHolder {

        @Bind(R.id.page_image_view) SubsamplingScaleImageView imageView;
        @Bind(R.id.progress) ProgressBar progressBar;

        public ImageHolder(View view, int maxBitmapSize, View.OnTouchListener listener) {
            super(view);
            ButterKnife.bind(this, view);

            imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
            imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
            imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
            imageView.setZoomEnabled(false);
            imageView.setPanEnabled(false);
            imageView.setOnTouchListener(listener);
            // TODO Using tiling here is annoying. RecyclerView isn't good at all for a webtoon reader
//            imageView.setMaxDimensions(maxBitmapSize, maxBitmapSize);
        }

        public void onSetValues(Page page) {
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
