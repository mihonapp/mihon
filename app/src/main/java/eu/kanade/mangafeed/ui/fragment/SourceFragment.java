package eu.kanade.mangafeed.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.presenter.SourcePresenter;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.activity.CatalogueActivity;
import eu.kanade.mangafeed.ui.activity.MainActivity;
import eu.kanade.mangafeed.ui.adapter.SourceHolder;
import nucleus.factory.RequiresPresenter;
import uk.co.ribot.easyadapter.EasyAdapter;

@RequiresPresenter(SourcePresenter.class)
public class SourceFragment extends BaseFragment<SourcePresenter> {

    @Bind(R.id.catalogue_list) ListView source_list;

    private MainActivity activity;
    private EasyAdapter<Source> adapter;

    public static SourceFragment newInstance() {
        return new SourceFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (MainActivity)getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_catalogue, container, false);
        activity.setToolbarTitle(R.string.catalogues_title);
        ButterKnife.bind(this, view);

        createAdapter();

        return view;
    }

    @OnItemClick(R.id.catalogue_list)
    public void onSourceClick(int position) {
        Source source = adapter.getItem(position);
        getPresenter().setSelectedSource(source);

        Intent intent = CatalogueActivity.newIntent(activity, source.getSourceId());
        startActivity(intent);
    }

    private void createAdapter() {
        adapter = new EasyAdapter<>(activity, SourceHolder.class);
        source_list.setAdapter(adapter);
    }

    public void setItems(List<Source> items) {
        adapter.setItems(items);
    }

}
