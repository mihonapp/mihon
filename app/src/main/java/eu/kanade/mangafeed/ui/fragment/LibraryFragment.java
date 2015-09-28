package eu.kanade.mangafeed.ui.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.presenter.LibraryPresenter;
import eu.kanade.mangafeed.ui.activity.MainActivity;
import eu.kanade.mangafeed.ui.adapter.MangaLibraryHolder;
import eu.kanade.mangafeed.view.LibraryView;
import uk.co.ribot.easyadapter.EasyAdapter;


public class LibraryFragment extends Fragment implements LibraryView {

    @Bind(R.id.gridView) GridView grid;
    LibraryPresenter presenter;
    EasyAdapter<Manga> adapter;

    public static LibraryFragment newInstance() {
        LibraryFragment fragment = new LibraryFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        presenter = new LibraryPresenter(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library, container, false);
        ((MainActivity)getActivity()).setToolbarTitle(getString(R.string.library_title));
        ButterKnife.bind(this, view);

        presenter.initializeMangas();
        setMangaClickListener();

        return view;
    }

    public void setMangas(List<Manga> mangas) {
        if (adapter == null) {
            adapter = new EasyAdapter<Manga>(
                    getActivity(),
                    MangaLibraryHolder.class,
                    mangas
            );
            grid.setAdapter(adapter);
        } else {
            adapter.setItems(mangas);
        }

    }

    private void setMangaClickListener() {
        grid.setOnItemClickListener(
                (parent, view, position, id) ->
                    presenter.onMangaClick(adapter, position)
        );
    }

}
