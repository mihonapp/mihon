package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
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
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.MangaChaptersPresenter;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import eu.kanade.mangafeed.ui.adapter.ChapterListHolder;
import nucleus.factory.RequiresPresenter;
import timber.log.Timber;
import uk.co.ribot.easyadapter.EasyRecyclerAdapter;

@RequiresPresenter(MangaChaptersPresenter.class)
public class MangaChaptersFragment extends BaseFragment<MangaChaptersPresenter> {

    @Bind(R.id.chapter_list) RecyclerView chapters;

    private long manga_id;
    private Manga manga;
    private EasyRecyclerAdapter<Chapter> adapter;

    public static Fragment newInstance(long manga_id) {
        MangaChaptersFragment fragment = new MangaChaptersFragment();
        Bundle args = new Bundle();
        args.putLong(MangaDetailActivity.MANGA_ID, manga_id);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setHasOptionsMenu(true);

        manga_id = getArguments().getLong(MangaDetailActivity.MANGA_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_manga_chapters, container, false);
        ButterKnife.bind(this, view);

        chapters.setLayoutManager(new LinearLayoutManager(getActivity()));
        createAdapter();

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
                getPresenter().refreshChapters(manga);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createAdapter() {
        adapter = new EasyRecyclerAdapter<>(getActivity(), ChapterListHolder.class);
        chapters.setAdapter(adapter);
    }

    public long getMangaId() {
        return manga_id;
    }

    public Manga getManga() {
        return manga;
    }

    public void onNextChapters(List<Chapter> chapters) {
        adapter.setItems(chapters);
    }

    public void onMangaNext(Manga manga) {
        this.manga = manga;
    }
}
