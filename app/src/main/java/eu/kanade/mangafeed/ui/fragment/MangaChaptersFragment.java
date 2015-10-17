package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.presenter.MangaChaptersPresenter;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(MangaChaptersPresenter.class)
public class MangaChaptersFragment extends BaseFragment<MangaChaptersPresenter> {

    private long manga_id;

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
        manga_id = getArguments().getLong(MangaDetailActivity.MANGA_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_manga_chapters, container, false);
        ButterKnife.bind(this, view);

        return view;
    }
}
