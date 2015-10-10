package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.presenter.CataloguePresenter;
import eu.kanade.mangafeed.ui.activity.MainActivity;
import eu.kanade.mangafeed.view.CatalogueView;
import uk.co.ribot.easyadapter.EasyAdapter;


public class CatalogueFragment extends BaseFragment implements CatalogueView {

    private CataloguePresenter presenter;
    private MainActivity activity;

    @Bind(R.id.catalogue_list)
    ListView source_list;

    public static CatalogueFragment newInstance() {
        CatalogueFragment fragment = new CatalogueFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        presenter = new CataloguePresenter(this);
        activity = (MainActivity)getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_catalogue, container, false);
        activity.setToolbarTitle(R.string.catalogues_title);
        ButterKnife.bind(this, view);

        presenter.initializeSources();

        return view;
    }

    // CatalogueView

    @Override
    public void setAdapter(EasyAdapter adapter) {
        source_list.setAdapter(adapter);
    }

    @Override
    public void setSourceClickListener() {
        source_list.setOnItemClickListener(
                (parent, view, position, id) ->
                    presenter.onSourceClick(position)
        );
    }
}
