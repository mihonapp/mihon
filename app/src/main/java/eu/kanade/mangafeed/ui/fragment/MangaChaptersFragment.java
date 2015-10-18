package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.presenter.MangaChaptersPresenter;
import eu.kanade.mangafeed.ui.adapter.ChapterListHolder;
import nucleus.factory.RequiresPresenter;
import uk.co.ribot.easyadapter.EasyRecyclerAdapter;

@RequiresPresenter(MangaChaptersPresenter.class)
public class MangaChaptersFragment extends BaseFragment<MangaChaptersPresenter> {

    @Bind(R.id.chapter_list) RecyclerView chapters;
    @Bind(R.id.swipe_refresh) SwipeRefreshLayout swipe_refresh;

    private EasyRecyclerAdapter<Chapter> adapter;

    public static Fragment newInstance() {
        return new MangaChaptersFragment();
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_manga_chapters, container, false);
        ButterKnife.bind(this, view);

        chapters.setLayoutManager(new LinearLayoutManager(getActivity()));
        createAdapter();
        setSwipeRefreshListener();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.chapters, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                getPresenter().refreshChapters(this);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createAdapter() {
        adapter = new EasyRecyclerAdapter<>(getActivity(), ChapterListHolder.class);
        chapters.setAdapter(adapter);
    }

    private void setSwipeRefreshListener() {
        swipe_refresh.setOnRefreshListener(() -> getPresenter().refreshChapters(this));
    }

    public void onNextChapters(List<Chapter> chapters) {
        adapter.setItems(chapters);
    }

    public void onNextOnlineChapters() {
        swipe_refresh.setRefreshing(false);
    }

    public void setSwipeRefreshing() {
        swipe_refresh.setRefreshing(true);
    }
}
