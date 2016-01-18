package eu.kanade.tachiyomi.ui.recent;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder;
import eu.kanade.tachiyomi.ui.base.fragment.BaseRxFragment;
import eu.kanade.tachiyomi.ui.decoration.DividerItemDecoration;
import eu.kanade.tachiyomi.ui.reader.ReaderActivity;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(RecentChaptersPresenter.class)
public class RecentChaptersFragment extends BaseRxFragment<RecentChaptersPresenter> implements FlexibleViewHolder.OnListItemClickListener {

    @Bind(R.id.chapter_list) RecyclerView recyclerView;

    private RecentChaptersAdapter adapter;

    public static RecentChaptersFragment newInstance() {
        return new RecentChaptersFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_recent_chapters, container, false);
        ButterKnife.bind(this, view);

        // Init RecyclerView and adapter
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.addItemDecoration(new DividerItemDecoration(ContextCompat.getDrawable(
                getContext(), R.drawable.line_divider)));
        recyclerView.setHasFixedSize(true);
        adapter = new RecentChaptersAdapter(this);
        recyclerView.setAdapter(adapter);

        setToolbarTitle(R.string.label_recent_updates);
        return view;
    }

    public void onNextMangaChapters(List<Object> chapters) {
        adapter.setItems(chapters);
    }

    @Override
    public boolean onListItemClick(int position) {
        Object item = adapter.getItem(position);
        if (item instanceof MangaChapter) {
            openChapter((MangaChapter) item);
        }
        return false;
    }

    @Override
    public void onListItemLongClick(int position) {

    }

    protected void openChapter(MangaChapter chapter) {
        getPresenter().onOpenChapter(chapter);
        Intent intent = ReaderActivity.newIntent(getActivity());
        startActivity(intent);
    }
}
