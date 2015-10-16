package eu.kanade.mangafeed.presenter;

import android.content.Intent;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.activity.CatalogueActivity;
import eu.kanade.mangafeed.ui.adapter.SourceHolder;
import eu.kanade.mangafeed.view.SourceView;
import uk.co.ribot.easyadapter.EasyAdapter;


public class SourcePresenter {

    private SourceView view;

    @Inject SourceManager sourceManager;

    EasyAdapter<Source> adapter;

    public SourcePresenter(SourceView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void initializeSources() {
        adapter = new EasyAdapter<>(
                view.getActivity(),
                SourceHolder.class,
                sourceManager.getSources());

        view.setAdapter(adapter);
        view.setSourceClickListener();
    }

    public void onSourceClick(int position) {
        sourceManager.setSelectedSource(adapter.getItem(position).getSource());
        Intent intent = new Intent(view.getActivity(), CatalogueActivity.class);
        intent.putExtra(Intent.EXTRA_UID, adapter.getItem(position).getSource());
        view.getActivity().startActivity(intent);
    }
}
