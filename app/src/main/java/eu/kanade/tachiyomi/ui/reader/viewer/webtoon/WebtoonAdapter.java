package eu.kanade.tachiyomi.ui.reader.viewer.webtoon;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.reader.ReaderActivity;

public class WebtoonAdapter extends RecyclerView.Adapter<WebtoonHolder> {

    private WebtoonReader fragment;
    private List<Page> pages;
    private View.OnTouchListener touchListener;

    public WebtoonAdapter(WebtoonReader fragment) {
        this.fragment = fragment;
        pages = new ArrayList<>();
        touchListener = (v, event) -> fragment.gestureDetector.onTouchEvent(event);
    }

    public Page getItem(int position) {
        return pages.get(position);
    }

    @Override
    public WebtoonHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = fragment.getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.item_webtoon_reader, parent, false);
        return new WebtoonHolder(v, this, touchListener);
    }

    @Override
    public void onBindViewHolder(WebtoonHolder holder, int position) {
        final Page page = getItem(position);
        holder.onSetValues(page);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
    }

    public void clear() {
        if (pages != null) {
            pages.clear();
            notifyDataSetChanged();
        }
    }

    public void retryPage(Page page) {
        fragment.getReaderActivity().getPresenter().retryPage(page);
    }

    public WebtoonReader getReader() {
        return fragment;
    }

    public ReaderActivity getReaderActivity() {
        return (ReaderActivity) fragment.getActivity();
    }

}
