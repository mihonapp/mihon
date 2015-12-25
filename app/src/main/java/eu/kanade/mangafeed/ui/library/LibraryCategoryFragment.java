package eu.kanade.mangafeed.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.ui.base.fragment.BaseFragment;
import eu.kanade.mangafeed.ui.manga.MangaActivity;

public class LibraryCategoryFragment extends BaseFragment {

    @Bind(R.id.gridView) GridView grid;

    private LibraryFragment parent;
    private LibraryAdapter adapter;
    private Category category;
    private List<Manga> mangas;

    public static LibraryCategoryFragment newInstance(LibraryFragment parent, Category category,
                                                      List<Manga> mangas) {
        LibraryCategoryFragment fragment = new LibraryCategoryFragment();
        fragment.initialize(parent, category, mangas);
        return fragment;
    }

    private void initialize(LibraryFragment parent, Category category, List<Manga> mangas) {
        this.parent = parent;
        this.category = category;
        this.mangas = mangas;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library_category, container, false);
        ButterKnife.bind(this, view);

        adapter = new LibraryAdapter(parent);
        grid.setAdapter(adapter);

        if (mangas != null) {
            setMangas(mangas);
        }

        return view;
    }

    @OnItemClick(R.id.gridView)
    protected void onMangaClick(int position) {
        Intent intent = MangaActivity.newIntent(
                getActivity(),
                adapter.getItem(position)
        );
        parent.getPresenter().onOpenManga();
        getActivity().startActivity(intent);
    }

    public void setMangas(List<Manga> mangas) {
        adapter.setNewItems(mangas);
    }

}
