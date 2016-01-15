package eu.kanade.tachiyomi.ui.catalogue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.jsoup.nodes.Document;

import java.util.List;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.source.base.Source;
import eu.kanade.tachiyomi.data.source.model.MangasPage;

public class CatalogueSpinnerAdapter extends ArrayAdapter<Source> {

    public CatalogueSpinnerAdapter(Context context, int resource, List<Source> sources) {
        super(context, resource, sources);
        sources.add(new SimpleSource());
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View v = super.getView(position, convertView, parent);
        if (position == getCount()) {
            ((TextView)v.findViewById(android.R.id.text1)).setText("");
            ((TextView)v.findViewById(android.R.id.text1)).setHint(getItem(getCount()).getName());
        }

        return v;
    }

    @Override
    public int getCount() {
        return super.getCount()-1; // you dont display last item. It is used as hint.
    }

    public int getEmptyIndex() {
        return getCount();
    }

    private class SimpleSource extends Source {

        @Override
        public String getName() {
            return getContext().getString(R.string.select_source);
        }

        @Override
        public int getId() {
            return -1;
        }

        @Override
        public String getBaseUrl() {
            return null;
        }

        @Override
        public boolean isLoginRequired() {
            return false;
        }

        @Override
        protected String getInitialPopularMangasUrl() {
            return null;
        }

        @Override
        protected String getInitialSearchUrl(String query) {
            return null;
        }

        @Override
        protected List<Manga> parsePopularMangasFromHtml(Document parsedHtml) {
            return null;
        }

        @Override
        protected String parseNextPopularMangasUrl(Document parsedHtml, MangasPage page) {
            return null;
        }

        @Override
        protected List<Manga> parseSearchFromHtml(Document parsedHtml) {
            return null;
        }

        @Override
        protected String parseNextSearchUrl(Document parsedHtml, MangasPage page, String query) {
            return null;
        }

        @Override
        protected Manga parseHtmlToManga(String mangaUrl, String unparsedHtml) {
            return null;
        }

        @Override
        protected List<Chapter> parseHtmlToChapters(String unparsedHtml) {
            return null;
        }

        @Override
        protected List<String> parseHtmlToPageUrls(String unparsedHtml) {
            return null;
        }

        @Override
        protected String parseHtmlToImageUrl(String unparsedHtml) {
            return null;
        }
    }

}
