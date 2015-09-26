package eu.kanade.mangafeed.ui.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.activity.BaseActivity;
import eu.kanade.mangafeed.ui.adapter.LibraryAdapter;
import rx.functions.Action1;

public class LibraryFragment extends Fragment {

    @Bind(R.id.gridView)
    GridView grid;

    @Inject
    DatabaseHelper db;

    List<Manga> mangas;

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
        App.getComponent(getActivity()).inject(this);
        ((BaseActivity) getActivity()).getSupportActionBar().setTitle(R.string.library_title);
        ButterKnife.bind(this, view);

        db.manga.get().subscribe(
                result -> {
                    mangas = result;

                    LibraryAdapter adapter = new LibraryAdapter(getActivity(),
                            R.layout.item_library, mangas);

                    grid.setAdapter(adapter);
                    grid.setOnItemClickListener(
                            (parent, v, position, id) -> {
                                Intent intent = new Intent(".ui.activity.MangaDetailActivity");
                                EventBus.getDefault().postSticky(adapter.getItem(position));
                                startActivity(intent);
                            }
                    );
                }
        );

        return view;
    }

}
