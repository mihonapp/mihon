package eu.kanade.mangafeed.ui.manga.info;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(MangaInfoPresenter.class)
public class MangaInfoFragment extends BaseRxFragment<MangaInfoPresenter> {

    @Bind(R.id.manga_artist) TextView mArtist;
    @Bind(R.id.manga_author) TextView mAuthor;
    @Bind(R.id.manga_chapters) TextView mChapters;
    @Bind(R.id.manga_genres) TextView mGenres;
    @Bind(R.id.manga_status) TextView mStatus;
    @Bind(R.id.manga_summary) TextView mDescription;
    @Bind(R.id.manga_cover) ImageView mCover;

    @Bind(R.id.action_favorite) Button favoriteBtn;


    public static MangaInfoFragment newInstance() {
        return new MangaInfoFragment();
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
        View view = inflater.inflate(R.layout.fragment_manga_info, container, false);
        ButterKnife.bind(this, view);

        favoriteBtn.setOnClickListener(v -> {
            getPresenter().toggleFavorite();
        });

        return view;
    }

    public void setMangaInfo(Manga manga) {
        mArtist.setText(manga.artist);
        mAuthor.setText(manga.author);
        mGenres.setText(manga.genre);
        mStatus.setText("Ongoing"); //TODO
        mDescription.setText(manga.description);

        setFavoriteText(manga.favorite);

        getPresenter().coverCache.loadOrFetchInto(mCover,
                manga.thumbnail_url, getPresenter().source.getGlideHeaders());

    }

    public void setChapterCount(int count) {
        mChapters.setText(String.valueOf(count));
    }

    public void setFavoriteText(boolean isFavorite) {
        favoriteBtn.setText(!isFavorite ? R.string.add_to_library : R.string.remove_from_library);
    }

}
