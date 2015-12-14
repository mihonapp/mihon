package eu.kanade.mangafeed.ui.manga.myanimelist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.MangaSync;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(MyAnimeListPresenter.class)
public class MyAnimeListFragment extends BaseRxFragment<MyAnimeListPresenter> {

    @Bind(R.id.myanimelist_title) TextView title;
    @Bind(R.id.last_chapter_read) EditText lastChapterRead;
    @Bind(R.id.score) TextView score;
    @Bind(R.id.status) TextView status;
    @Bind(R.id.update_button) Button updateButton;

    private MyAnimeListDialogFragment dialog;

    private DecimalFormat decimalFormat = new DecimalFormat("#.##");

    public static MyAnimeListFragment newInstance() {
        return new MyAnimeListFragment();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_myanimelist, container, false);
        ButterKnife.bind(this, view);

        updateButton.setOnClickListener(v -> getPresenter().updateLastChapter(
                Integer.parseInt(lastChapterRead.getText().toString())));

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.myanimelist, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.myanimelist_edit:
                showSearchDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void setMangaSync(MangaSync mangaSync) {
        title.setText(mangaSync.title);
        lastChapterRead.setText(mangaSync.last_chapter_read + "");
        score.setText(decimalFormat.format(mangaSync.score));
        status.setText(getPresenter().myAnimeList.getStatus(mangaSync.status));
    }

    private void showSearchDialog() {
        if (dialog == null)
            dialog = MyAnimeListDialogFragment.newInstance(this);

        dialog.show(getActivity().getSupportFragmentManager(), "search");
    }

    public void onSearchResults(List<MangaSync> results) {
        if (dialog != null)
            dialog.setResults(results);
    }
}
