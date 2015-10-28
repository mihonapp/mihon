package eu.kanade.mangafeed.ui.viewer;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.List;

import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.ui.adapter.WebtoonAdapter;
import eu.kanade.mangafeed.ui.viewer.base.BaseViewer;

public class WebtoonViewer extends BaseViewer {

    private RecyclerView recycler;
    private WebtoonAdapter adapter;

    public WebtoonViewer(ReaderActivity activity, FrameLayout container) {
        super(activity, container);

        recycler = new RecyclerView(activity);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new WebtoonAdapter(activity);
        recycler.setAdapter(adapter);

        container.addView(recycler);
    }

    @Override
    public int getTotalPages() {
        return adapter.getItemCount();
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        // TODO
        return;
    }

    @Override
    public void onPageListReady(List<Page> pages) {
        adapter.setPages(pages);
    }

    @Override
    public boolean onImageTouch(MotionEvent motionEvent) {
        return true;
    }
}
