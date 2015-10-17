package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.MangaInfoPresenter;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(MangaInfoPresenter.class)
public class MangaInfoFragment extends BaseFragment<MangaInfoPresenter> {

    @Bind(R.id.manga_artist) TextView mArtist;
    @Bind(R.id.manga_author) TextView mAuthor;
    @Bind(R.id.manga_chapters) TextView mChapters;
    @Bind(R.id.manga_genres) TextView mGenres;
    @Bind(R.id.manga_status) TextView mStatus;
    @Bind(R.id.manga_summary) TextView mDescription;
    @Bind(R.id.manga_cover) ImageView mCover;

    private long manga_id;

    public static MangaInfoFragment newInstance(long manga_id) {
        MangaInfoFragment fragment = new MangaInfoFragment();
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
        View view = inflater.inflate(R.layout.fragment_manga_info, container, false);
        ButterKnife.bind(this, view);

        return view;
    }

    public long getMangaId() {
        return manga_id;
    }

    public void setMangaInfo(Manga manga) {
        mArtist.setText(manga.artist);
        mAuthor.setText(manga.author);
        mChapters.setText("0"); // TODO
        mGenres.setText(manga.genre);
        mStatus.setText("Ongoing"); //TODO
        mDescription.setText(manga.description);

        Glide.with(getActivity())
                .load(manga.thumbnail_url)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .centerCrop()
                .into(mCover);
    }
}
