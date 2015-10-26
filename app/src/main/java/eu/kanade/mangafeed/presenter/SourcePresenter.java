package eu.kanade.mangafeed.presenter;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.sources.base.Source;
import eu.kanade.mangafeed.ui.fragment.SourceFragment;


public class SourcePresenter extends BasePresenter<SourceFragment> {

    @Inject SourceManager sourceManager;
    @Inject PreferencesHelper prefs;

    @Override
    protected void onTakeView(SourceFragment view) {
        super.onTakeView(view);

        view.setItems(sourceManager.getSources());
    }

    public boolean isValidSource(Source source) {
        if (!source.isLoginRequired() || source.isLogged())
            return true;

        return !(prefs.getSourceUsername(source).equals("")
                || prefs.getSourcePassword(source).equals(""));
    }
}
