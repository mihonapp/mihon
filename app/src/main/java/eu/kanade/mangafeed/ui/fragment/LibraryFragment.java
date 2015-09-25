package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.entities.Manga;
import eu.kanade.mangafeed.ui.activity.BaseActivity;
import eu.kanade.mangafeed.ui.adapter.LibraryAdapter;

public class LibraryFragment extends Fragment {

    @Bind(R.id.gridView)
    GridView grid;

    public static LibraryFragment newInstance() {
        LibraryFragment fragment = new LibraryFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library, container, false);
        ((BaseActivity) getActivity()).getSupportActionBar().setTitle(R.string.library_title);

        ButterKnife.bind(this, view);

        ArrayList<Manga> mangas = new ArrayList<>();
        mangas.add(new Manga("One Piece"));
        mangas.add(new Manga("Berserk"));
        mangas.add(new Manga("Fate/stay night: Unlimited Blade Works"));

        LibraryAdapter adapter = new LibraryAdapter(getActivity(),
                R.layout.item_library, mangas);

        grid.setAdapter(adapter);

        return view;
    }

}
