package eu.kanade.mangafeed.ui.manga.info;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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

    private MenuItem favoriteBtn;
    private MenuItem removeFavoriteBtn;

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

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.manga_info, menu);
        favoriteBtn = menu.findItem(R.id.action_favorite);
        removeFavoriteBtn = menu.findItem(R.id.action_remove_favorite);
        getPresenter().initFavoriteIcon();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
            case R.id.action_remove_favorite:
                getPresenter().toggleFavorite();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void setMangaInfo(Manga manga) {
        mArtist.setText(manga.artist);
        mAuthor.setText(manga.author);
        mGenres.setText(manga.genre);
        mStatus.setText("Ongoing"); //TODO
        mDescription.setText(manga.description);

        setFavoriteIcon(manga.favorite);

        Glide.with(getActivity())
                .load(manga.thumbnail_url)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .centerCrop()
                .into(mCover);
    }

    public void setChapterCount(int count) {
        mChapters.setText(String.valueOf(count));
    }

    public void setFavoriteIcon(boolean isFavorite) {
        if (favoriteBtn != null) favoriteBtn.setVisible(!isFavorite);
        if (removeFavoriteBtn != null) removeFavoriteBtn.setVisible(isFavorite);
    }

}
